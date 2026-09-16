package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CapturaCuponRepository;
import com.franco.dev.service.CrudService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.financiero.FormatoTerminalPosRegion;
import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.repository.financiero.FormatoTerminalPosRegionRepository;
import com.franco.dev.service.financiero.ocr.DerivadorMapa;
import com.franco.dev.service.financiero.ocr.MotorOcr;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.ocr.CuponOcrService;
import com.franco.dev.service.financiero.ocr.ExtractorCupon;
import com.franco.dev.service.financiero.ocr.MotorOcr;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Coordina la captura de la foto del cupon: crea el token que viaja en el QR, recibe la imagen
 * del telefono y le pasa el OCR.
 */
@Slf4j
@Service
public class CapturaCuponService extends CrudService<CapturaCupon, CapturaCuponRepository> {

    /**
     * Suficiente para que el cajero saque la foto sin que el QR quede vivo toda la tarde.
     * <p>
     * Sigue siendo el default, pero ya no es la ultima palabra: lo configura
     * {@code configuracion_venta_tarjeta.minutos_validez_captura}. Se usa cuando la fila todavia
     * no bajo por replicacion, que en un filial recien migrado es un caso real.
     */
    private static final int MINUTOS_VALIDEZ = 10;

    /** 32 bytes de entropia real: el token es la unica credencial del telefono. */
    private static final int BYTES_TOKEN = 24;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter CARPETA = DateTimeFormatter.ofPattern("yyyy/MM");

    private final CapturaCuponRepository repository;
    private final CuponOcrService ocr;

    private final ExtractorCupon extractor;

    /** Para no confiar en el sucursalId que manda el cliente. */
    private final SucursalService sucursales;

    /** El mapa del formato, para acotar el reconocimiento. */
    private final FormatoTerminalPosRegionRepository regiones;

    private final DerivadorMapa derivador;
    private final PdvCajaService cajas;

    /** De aca sale la vida del token. Ver {@link #MINUTOS_VALIDEZ}. */
    private final ConfiguracionVentaTarjetaService configuracion;

    /**
     * Relativa al directorio de trabajo del servicio (/opt/frc-filial en produccion). Se crea
     * sola en el primer guardado: no hay nada que provisionar antes del despliegue.
     */
    private final String rutaImagenes;

    /**
     * Con que direccion se arma el QR. Vacio = se deduce mirando las interfaces de red.
     * Se configura solo si la deduccion elige mal (varias LAN, NAT de por medio).
     */
    private final String baseUrlConfigurada;

    private final int puerto;

    @Autowired
    public CapturaCuponService(CapturaCuponRepository repository,
                               CuponOcrService ocr,
                               ExtractorCupon extractor,
                               SucursalService sucursales,
                               FormatoTerminalPosRegionRepository regiones,
                               DerivadorMapa derivador,
                               PdvCajaService cajas,
                               ConfiguracionVentaTarjetaService configuracion,
                               @Value("${frc.captura.ruta-imagenes:cupones}") String rutaImagenes,
                               @Value("${frc.captura.base-url:}") String baseUrlConfigurada,
                               @Value("${server.port:8081}") int puerto) {
        this.repository = repository;
        this.ocr = ocr;
        this.extractor = extractor;
        this.sucursales = sucursales;
        this.regiones = regiones;
        this.derivador = derivador;
        this.cajas = cajas;
        this.configuracion = configuracion;
        this.rutaImagenes = rutaImagenes;
        this.baseUrlConfigurada = baseUrlConfigurada;
        this.puerto = puerto;
    }

    @Override
    public CapturaCuponRepository getRepository() {
        return repository;
    }

    /**
     * Abre una captura.
     *
     * @param terminalPos de que aparato es la foto. Puede venir {@code null} --un cliente viejo
     *                    no lo manda-- y en ese caso se guarda el texto leido sin extraer campos,
     *                    que es lo que el modulo hacia antes de la etapa 4.
     */
    @Transactional
    public CapturaCupon crear(Long cajaId, Long sucursalId, Usuario usuario, TerminalPos terminalPos) {
        // La sucursal la decide el servidor, no el cliente. captura_cupon no se replica, asi que
        // aca no hay fuga cross-tenant --el campo es de auditoria-- pero una auditoria que miente
        // no sirve, y este es codigo nuevo: no tiene por que nacer con el defecto que el resto
        // del modulo arrastra.
        Long suc = sucursales.exigirSucursalPropia(sucursalId);

        // La caja tiene que estar abierta YA, no solo cuando llegue la foto. Se validaba al
        // consumir el token y no al emitirlo: eso dejaba al cajero escanear el QR, ir hasta el
        // aparato, sacar la foto y recien ahi enterarse de que la caja estaba cerrada. Es el
        // mismo chequeo, adelantado al momento en que todavia se puede hacer algo.
        Optional<PdvCaja> caja = cajas.findById(cajaId);
        if (!caja.isPresent() || caja.get().getEstado() != PdvCajaEstado.EN_PROCESO) {
            throw new IllegalStateException("la caja no esta abierta");
        }

        byte[] bytes = new byte[BYTES_TOKEN];
        RANDOM.nextBytes(bytes);

        CapturaCupon c = new CapturaCupon();
        c.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        c.setCajaId(cajaId);
        c.setSucursalId(suc);
        c.setUsuario(usuario);
        c.setTerminalPos(terminalPos);
        c.setEstado(CapturaCupon.ESPERANDO);
        c.setExpiraEn(LocalDateTime.now().plusMinutes(minutosValidez()));
        return repository.save(c);
    }

    /**
     * Minutos de vida del QR de captura.
     * <p>
     * Se lee en cada creacion y no se cachea: son pocas por dia y la fila puede cambiar por
     * replicacion en cualquier momento. Cachearla haria que un filial siga usando el valor viejo
     * hasta el proximo reinicio, sin que nadie entienda por que.
     */
    private int minutosValidez() {
        try {
            return configuracion.findOrDefault().minutosValidezCapturaEfectivo();
        } catch (Throwable e) {
            // Throwable y no Exception, por la misma razon que CuponOcrService: en este repo ya
            // hubo un arranque caido por un UnsatisfiedLinkError --que es un Error, no una
            // Exception-- que un catch (Exception) dejo pasar. Y sobre todo: que no se pueda leer
            // la configuracion no puede impedir sacar una foto. La captura es el camino de salida
            // cuando el POS no imprime QR; si falla, el cajero no tiene ninguno.
            log.warn("no se pudo leer minutos_validez_captura, usando el default de {} min", MINUTOS_VALIDEZ, e);
            return MINUTOS_VALIDEZ;
        }
    }

    public Optional<CapturaCupon> porToken(String token) {
        return repository.findByToken(token);
    }

    /**
     * Recibe la foto, guarda la imagen y corre el OCR.
     *
     * <p>Consume el token en el mismo paso: aunque el OCR falle, el token no vuelve a servir. La
     * imagen se guarda igual, porque es la evidencia y permite completar a mano despues.
     *
     * @return la captura ya actualizada, con el texto o con el error
     */
    @Transactional
    public CapturaCupon procesar(String token, byte[] jpeg, BigDecimal nitidez) {
        CapturaCupon c = repository.lockByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("codigo desconocido"));

        // Reintento sobre algo que ya salio bien: se devuelve el resultado en vez de un error.
        // Sin esto, perder la RESPUESTA --no la request-- deja al cajero trabado con un "ya se
        // uso" aunque del lado del servidor todo haya funcionado.
        if (CapturaCupon.LISTO.equals(c.getEstado())) return c;

        if (c.yaSeUso())     throw new IllegalStateException("este codigo ya se uso");
        if (c.estaVencida()) throw new IllegalStateException("el codigo vencio; pedi uno nuevo desde la caja");

        // La caja pudo cerrarse en los minutos que el telefono tuvo la pagina abierta.
        Optional<PdvCaja> caja = cajas.findById(c.getCajaId());
        if (!caja.isPresent() || caja.get().getEstado() != PdvCajaEstado.EN_PROCESO) {
            throw new IllegalStateException("la caja ya no esta abierta");
        }

        if (!ocr.disponible()) {
            throw new IllegalStateException("el lector no esta disponible; carga el cupon a mano");
        }

        c.setIntentos(c.getIntentos() == null ? 1 : c.getIntentos() + 1);
        c.setNitidez(nitidez);
        c.setEstado(CapturaCupon.PROCESANDO);
        c.setError(null);

        try {
            c.setImagenUrl(guardar(c, jpeg));
        } catch (Exception e) {
            // Que no se pueda archivar la evidencia no tiene por que impedir leer el cupon.
            log.error("no se pudo guardar la imagen de la captura {}", c.getId(), e);
        }

        try {
            // Acotado al mapa del formato, si tiene. Es la palanca de rendimiento: reconocer 6
            // cajas en vez de 26 baja `rec` de 3.841 a ~900 ms. Sin mapa se lee el cupon entero,
            // que es lo que el modulo hacia hasta ahora.
            MotorOcr.Resultado r = ocr.leer(jpeg, zonasDe(c));
            if (r.lineas.isEmpty()) {
                // Leyo cero: casi siempre es que la foto no es del cupon, o esta ilegible.
                // NO se consume el token: el cajero saca otra sin volver a la caja.
                c.setEstado(CapturaCupon.ERROR);
                c.setError("no se leyo nada; asegurate de que la foto sea del cupon");
            } else {
                // textoPorRenglones y no joining("\n"): el detector separa por componentes
                // conexos, asi que la etiqueta y su valor salen en cajas distintas aunque esten
                // en el mismo renglon del papel. Unirlas todas con un salto mete un \n que en el
                // ticket no existe, y el patron --escrito contra una cadena de QR, que nunca trae
                // saltos-- deja de matchear.
                c.setTextoOcr(r.textoPorRenglones());
                c.setMsOcr((int) r.msTotal);
                c.setEstado(CapturaCupon.LISTO);
                c.setUsadoEn(LocalDateTime.now());   // el token se consume RECIEN con un resultado bueno
                extraerCampos(c, r);
            }
        } catch (Exception e) {
            log.error("fallo el OCR de la captura {}", c.getId(), e);
            c.setEstado(CapturaCupon.ERROR);
            c.setError(recortar(e.getMessage()));
        }
        return repository.save(c);
    }

    /**
     * Las zonas del mapa del formato de esta captura, para acotar el reconocimiento.
     *
     * <p>Solo las regiones que tienen las cuatro coordenadas: una region anclada solo por
     * etiqueta no dice donde mirar, y mezclarla acotaria mal.
     *
     * <p>Devuelve {@code null} --y no una lista vacia-- cuando no hay nada que acotar, porque
     * vacia y "sin filtro" tienen que significar lo mismo del lado del motor y es mas claro
     * decirlo aca.
     */
    private List<MotorOcr.Zona> zonasDe(CapturaCupon c) {
        if (c.getTerminalPos() == null || c.getTerminalPos().getFormatoTerminalPos() == null) return null;
        Long formatoId = c.getTerminalPos().getFormatoTerminalPos().getId();
        if (formatoId == null) return null;

        List<MotorOcr.Zona> zonas = new ArrayList<MotorOcr.Zona>();
        for (FormatoTerminalPosRegion r : regiones.findByFormatoTerminalPos_IdOrderByOrdenAscIdAsc(formatoId)) {
            if (!r.tienePista()) continue;
            zonas.add(new MotorOcr.Zona(r.getX1().doubleValue(), r.getY1().doubleValue(),
                                        r.getX2().doubleValue(), r.getY2().doubleValue()));
        }
        return zonas.isEmpty() ? null : zonas;
    }

    /**
     * Separa el texto leido en campos, usando el formato del aparato.
     *
     * <p><b>Nunca cambia el estado de la captura.</b> Que no se pueda extraer no es un error de
     * la captura: la foto se leyo, el texto esta, y el cajero lo tiene en pantalla para cargar a
     * mano. Degradar a "texto sin campos" es exactamente lo que el modulo hacia antes de esta
     * etapa, asi que es un piso conocido y no una regresion.
     *
     * <p>Por eso el motivo del fallo va al log y no a {@code error}: ese campo es lo que el
     * desktop le muestra al cajero como "saca otra foto", y una foto perfecta con un patron mal
     * cargado no se arregla sacando otra.
     */
    private void extraerCampos(CapturaCupon c, MotorOcr.Resultado lectura) {
        if (c.getTerminalPos() == null || c.getTerminalPos().getFormatoTerminalPos() == null) {
            return;   // cliente viejo, o terminal sin formato: se queda con el texto
        }
        try {
            ExtractorCupon.Resultado r = extractor.extraer(c.getTextoOcr(),
                    c.getTerminalPos().getFormatoTerminalPos());
            if (!r.ok()) {
                log.info("captura {}: no se extrajeron campos ({})", c.getId(), r.error);
                return;
            }
            Map<String, Object> salida = new LinkedHashMap<String, Object>(r.campos);
            if (!r.extras.isEmpty()) salida.put("datosExtra", r.extras);

            Map<String, Object> confianzas = confianzaPorCampo(r, lectura);
            descontarPorTipo(c, r, confianzas);
            if (!confianzas.isEmpty()) salida.put("confianzas", confianzas);

            c.setCampos(new ObjectMapper().writeValueAsString(salida));
        } catch (Exception e) {
            log.error("captura {}: fallo la extraccion de campos", c.getId(), e);
        }
    }

    /**
     * Saca del mapa de confianzas los campos cuyo valor no encaja con el tipo que el mapa declara.
     *
     * <p><b>Contra que defiende.</b> El modo de falla caro del OCR no es no leer: es <b>leer
     * plausible y mal</b>. Un {@code O} por {@code 0} en un codigo de autorizacion sale con
     * confianza optica alta --el caracter se vio nitido, solo que era otro-- y el semaforo lo
     * pintaria verde. Despues ese cobro se concilia contra un codigo que no existe.
     *
     * <p><b>Por que sacarlo y no bajarlo a un numero.</b> Un campo sin entrada en {@code confianzas}
     * ya significa "de este no se sabe" y el desktop lo trata como dudoso; esta documentado en
     * {@link #confianzaPorCampo}. Inventar un 0.0 diria que el OCR leyo mal, y no es cierto: leyo
     * bien algo que no corresponde. El motivo es distinto, la accion del cajero es la misma.
     *
     * <p><b>Y por que no se rechaza la lectura.</b> Porque el tipo se dedujo de una sola foto de
     * muestra. Si esa muestra mintio --un numero de boleta que en ese cupon salio sin letras pero
     * en otros las tiene-- rechazar convertiria un formato util en uno inservible. Mandar a revisar
     * es reversible; rechazar, no.
     */
    private void descontarPorTipo(CapturaCupon c, ExtractorCupon.Resultado extraido,
                                  Map<String, Object> confianzas) {
        if (confianzas.isEmpty()) return;
        if (c.getTerminalPos() == null || c.getTerminalPos().getFormatoTerminalPos() == null) return;
        Long formatoId = c.getTerminalPos().getFormatoTerminalPos().getId();
        if (formatoId == null) return;

        for (FormatoTerminalPosRegion r : regiones.findByFormatoTerminalPos_IdOrderByOrdenAscIdAsc(formatoId)) {
            String tipo = r.getTipo();
            if (tipo == null || "TEXTO".equals(tipo)) continue;   // TEXTO no restringe nada
            if (!confianzas.containsKey(r.getCampo())) continue;

            Object valor = extraido.campos.get(r.getCampo());
            if (valor == null) continue;
            if (encaja(valor.toString(), tipo)) continue;

            confianzas.remove(r.getCampo());
            log.info("captura {}: el campo {} vino \"{}\" y el mapa lo declara {}; va a revision",
                    c.getId(), r.getCampo(), valor, tipo);
        }
    }

    /** Si un valor leido encaja con el tipo que el mapa declara para ese campo. */
    private static boolean encaja(String valor, String tipo) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) return true;   // vacio es problema de otro control, no de tipo
        if ("NUMERO".equals(tipo)) return v.matches("\\d[\\d.,]*");
        if ("FECHA".equals(tipo)) {
            // La forma normalizada que devuelve ExtractorCupon (ISO local, con o sin segundos) va
            // PRIMERO: es la que llega cuando el mapeo declara `formato`, y sin esta linea todo
            // cupon con fecha bien leida se marcaba dudoso por no parecerse a dd/MM/yyyy.
            return v.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?")
                    || v.matches("\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}")
                    || v.matches("\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}");
        }
        return true;
    }

    /**
     * Cuanta confianza tiene cada campo, para el semaforo del desktop.
     *
     * <p><b>Por que no alcanza con la confianza de la foto.</b> Esta medido que el promedio por
     * foto no distingue una linea buena de una mala: un cupon con el monto ilegible y el resto
     * perfecto promedia alto. Sabiendo en que tramo del texto cayo cada campo, la confianza deja
     * de ser un numero decorativo y pasa a decir <b>que dato hay que preguntar</b>.
     *
     * <p><b>Va como mapa aparte y no adentro de cada campo.</b> {@code campos} es el objeto que el
     * desktop ya lee para llenar el formulario; meterle un nivel --{@code monto: {valor, confianza}}--
     * romperia a cualquier cliente que hoy lee {@code campos.monto}. Un {@code confianzas} paralelo
     * es aditivo: el que no lo conoce lo ignora y sigue funcionando igual.
     *
     * <p>Un campo <b>sin</b> entrada aca es un campo del que no se sabe, y el desktop lo tiene que
     * tratar como dudoso. No confundir con confianza baja: son distintos motivos para preguntar,
     * pero la accion es la misma.
     */
    private Map<String, Object> confianzaPorCampo(ExtractorCupon.Resultado extraido,
                                                  MotorOcr.Resultado lectura) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (lectura == null) return out;
        for (Map.Entry<String, int[]> e : extraido.rangos.entrySet()) {
            int[] rango = e.getValue();
            Float conf = lectura.confianzaEnRango(rango[0], rango[1]);
            if (conf != null) out.put(e.getKey(), conf);
        }
        return out;
    }

    /**
     * Deriva el mapa del formato a partir de una captura ya tomada.
     *
     * <p>Vuelve a correr el OCR sobre la imagen guardada, y a proposito: lo que quedo en la base
     * es el <b>texto</b>, y para derivar hacen falta las <b>coordenadas</b>. Es una pasada mas,
     * pero ocurre una vez, cuando se configura un formato, no en cada cobro.
     *
     * <p>Deliberadamente <b>no persiste nada</b>. Ver {@code RegionDerivada}: las regiones son de
     * central.
     */
    public DerivadorMapa.Resultado derivarMapa(String token) {
        CapturaCupon c = repository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("no existe esa captura"));
        if (c.getTerminalPos() == null || c.getTerminalPos().getFormatoTerminalPos() == null) {
            return DerivadorMapa.Resultado.fallo("la captura no tiene formato asociado");
        }
        if (c.getImagenUrl() == null) {
            return DerivadorMapa.Resultado.fallo("la foto de esa captura ya no esta en el disco");
        }
        try {
            byte[] jpeg = Files.readAllBytes(Paths.get(c.getImagenUrl()));
            int[] tam = ocr.tamano(jpeg);
            // Sin acotar: para derivar el mapa hay que ver el cupon entero, justamente porque
            // todavia no hay mapa.
            MotorOcr.Resultado r = ocr.leer(jpeg, null);
            // El mapeo va junto con el patron: sin el, la region saldria nombrada con el GRUPO
            // --`auth`-- y no con la clave del mapeo --`codigoAutorizacion`--, y el ABM del central
            // la rechazaria. Medido de punta a punta el 2026-09-12.
            return derivador.derivar(r.lineas,
                    c.getTerminalPos().getFormatoTerminalPos().getPatron(),
                    c.getTerminalPos().getFormatoTerminalPos().getMapeo(),
                    tam[0], tam[1]);
        } catch (Exception e) {
            log.error("no se pudo derivar el mapa de la captura {}", c.getId(), e);
            return DerivadorMapa.Resultado.fallo("no se pudo leer la foto guardada");
        }
    }

    /** {@code <ruta>/yyyy/MM/<id>.jpg}, con el directorio creado al vuelo. */
    private String guardar(CapturaCupon c, byte[] jpeg) throws Exception {
        Path dir = Paths.get(rutaImagenes, LocalDateTime.now().format(CARPETA));
        Files.createDirectories(dir);
        Path destino = dir.resolve(c.getId() + ".jpg");
        Files.write(destino, jpeg);
        return destino.toString();
    }

    private static String recortar(String s) {
        if (s == null) return "error sin detalle";
        return s.length() > 480 ? s.substring(0, 480) : s;
    }

    /**
     * La URL que va dentro del QR.
     *
     * <p>Tiene que ser la direccion por la que el <b>telefono</b> alcanza a este filial. Se
     * resuelve en tres pasos, del mas confiable al menos:
     *
     * <ol>
     *   <li><b>{@code frc.captura.base-url}</b>, si esta configurada. Manda siempre.</li>
     *   <li><b>La direccion por la que entro esta misma request.</b> Quien pide la captura es el
     *       desktop de la caja, que esta en la misma red que el telefono; la interfaz por la que
     *       llego es, por definicion, una que funciona desde el piso del local. Es una medicion,
     *       no una deduccion.</li>
     *   <li><b>El escaneo de interfaces</b>, como ultimo recurso.</li>
     * </ol>
     *
     * <p><b>Por que el escaneo quedo de ultimo.</b> Un filial convive con la LAN del local y con
     * una o dos redes overlay, y <b>ni el rango ni el nombre las distinguen</b>. Verificado el
     * 2026-09-10: en esta red 172.25/16 es ZeroTier, no la LAN --y ZeroTier se presenta como
     * {@code zt*} en Linux, {@code feth*} en macOS y con un nombre cualquiera en Windows, con
     * direccion privada y broadcast, indistinguible de una placa de red de verdad. Elegir mal no
     * da error: el QR se dibuja igual y el telefono no carga nada.
     *
     * <p>Es HTTP a proposito: origen privado hacia privado, sin contenido mixto ni permiso de red
     * local, asi anda igual en Safari que en Chrome sin certificado. Ver §2.8 de
     * FASE-2-TICKET-FISICO.md.
     */
    public String urlDe(CapturaCupon c) {
        String base = baseUrlConfigurada != null && !baseUrlConfigurada.trim().isEmpty()
                ? baseUrlConfigurada.trim().replaceAll("/+$", "")
                : "http://" + host() + ":" + puerto;
        return base + "/public/captura/" + c.getToken();
    }

    /**
     * La direccion de esta maquina por la que llego la request, o el escaneo si no hay request.
     *
     * <p>Deja dicho en el log de donde salio. Si el resolver corriera fuera del hilo del servlet
     * --async, un executor propio-- {@code RequestContextHolder} vendria vacio y esto caeria al
     * escaneo sin que se note: el QR se dibujaria igual, apuntando a la interfaz equivocada. Una
     * linea por captura, y es lo primero que hay que mirar cuando un telefono no carga la
     * pagina.
     */
    private static String host() {
        String delRequest = ipDeLaRequest();
        if (delRequest != null) {
            log.info("QR de captura apuntando a {} (de la request)", delRequest);
            return delRequest;
        }
        String escaneada = ipLan();
        log.info("QR de captura apuntando a {} (del escaneo de interfaces; la request no sirvio)",
                escaneada);
        return escaneada;
    }

    /**
     * La IP local del socket de la request en curso.
     *
     * <p>Devuelve null cuando no hay request (un test, un scheduler) o cuando la que hay no
     * sirve: loopback --el desktop corriendo en la misma maquina-- o una direccion publica.
     */
    private static String ipDeLaRequest() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes)) return null;

            String ip = ((ServletRequestAttributes) attrs).getRequest().getLocalAddr();
            if (ip == null) return null;

            InetAddress dir = InetAddress.getByName(ip);
            if (!(dir instanceof Inet4Address) || dir.isLoopbackAddress()) return null;
            if (!dir.isSiteLocalAddress() || esCgnat(ip)) return null;
            return ip;
        } catch (Exception e) {
            return null;
        }
    }

    /** Primera IPv4 privada que no sea de tailscale, docker ni loopback. */
    private static String ipLan() {
        try {
            List<NetworkInterface> nics = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nic : nics) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                if (!interfazUtil(nic.getName())) continue;

                for (InetAddress dir : Collections.list(nic.getInetAddresses())) {
                    if (!(dir instanceof Inet4Address) || dir.isLoopbackAddress()) continue;
                    if (direccionUtil(dir.getHostAddress(), dir.isSiteLocalAddress())) {
                        return dir.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.error("no se pudo deducir la ip de la LAN para el QR de captura", e);
        }
        // Ultimo recurso: el desktop va a mostrar un QR que no resuelve y el cajero va a avisar.
        // Es preferible a no mostrar nada: el sintoma dice donde mirar.
        return "127.0.0.1";
    }

    /**
     * Si una interfaz puede ser la del wifi del local.
     *
     * <p>Se descartan por nombre las que sabemos que no lo son: {@code tailscale0} en las que ya
     * migraron de ZeroTier, {@code zt*} (Linux) y {@code feth*} (macOS) en las que todavia lo
     * usan, {@code docker0} donde corre algo en contenedor, y los bridges que Docker y libvirt
     * crean solos ({@code br-*}, {@code veth*}, {@code virbr*}) y que {@code isVirtual()} no
     * siempre marca.
     *
     * <p><b>El filtro por nombre es debil y por eso este camino es el ultimo.</b> ZeroTier se
     * llama distinto en cada sistema y en Windows ni siquiera empieza por {@code zt}. Lo que de
     * verdad decide es la direccion por la que entro la request; ver {@code urlDe}.
     *
     * <p>Package-private para poder testearla: es la parte con criterio, y no se puede ejercitar
     * pidiendole a la maquina de turno que tenga las interfaces del caso.
     */
    static boolean interfazUtil(String nombre) {
        if (nombre == null) return false;
        String n = nombre.toLowerCase();
        return !(n.startsWith("tailscale") || n.startsWith("zt") || n.startsWith("feth")
                || n.startsWith("docker") || n.startsWith("br-")
                || n.startsWith("veth") || n.startsWith("virbr"));
    }

    /**
     * Si una direccion sirve para que un telefono del local alcance a este filial.
     *
     * <p>Tiene que ser privada --una publica no la va a alcanzar el telefono, y ademas no
     * queremos publicar la pagina hacia afuera-- y no puede ser de las dos redes privadas que
     * conviven con la LAN sin ser la LAN: tailscale (100.64/10, que Java ni siquiera considera
     * site-local) y la de docker (172.17/16, que si lo es y por eso hay que nombrarla).
     */
    static boolean direccionUtil(String ip, boolean siteLocal) {
        if (ip == null || !siteLocal) return false;
        return !esCgnat(ip) && !ip.startsWith("172.17.");
    }

    /** 100.64.0.0/10, el rango que usa tailscale. */
    private static boolean esCgnat(String ip) {
        if (!ip.startsWith("100.")) return false;
        try {
            int segundo = Integer.parseInt(ip.split("\\.")[1]);
            return segundo >= 64 && segundo <= 127;
        } catch (Exception e) {
            return false;
        }
    }
}
