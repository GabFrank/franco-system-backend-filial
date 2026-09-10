package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CapturaCuponRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.ocr.CuponOcrService;
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
                               PdvCajaService cajas,
                               ConfiguracionVentaTarjetaService configuracion,
                               @Value("${frc.captura.ruta-imagenes:cupones}") String rutaImagenes,
                               @Value("${frc.captura.base-url:}") String baseUrlConfigurada,
                               @Value("${server.port:8081}") int puerto) {
        this.repository = repository;
        this.ocr = ocr;
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

    @Transactional
    public CapturaCupon crear(Long cajaId, Long sucursalId, Usuario usuario) {
        byte[] bytes = new byte[BYTES_TOKEN];
        RANDOM.nextBytes(bytes);

        CapturaCupon c = new CapturaCupon();
        c.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        c.setCajaId(cajaId);
        c.setSucursalId(sucursalId);
        c.setUsuario(usuario);
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
            MotorOcr.Resultado r = ocr.leer(jpeg);
            if (r.lineas.isEmpty()) {
                // Leyo cero: casi siempre es que la foto no es del cupon, o esta ilegible.
                // NO se consume el token: el cajero saca otra sin volver a la caja.
                c.setEstado(CapturaCupon.ERROR);
                c.setError("no se leyo nada; asegurate de que la foto sea del cupon");
            } else {
                c.setTextoOcr(r.lineas.stream().map(l -> l.texto).collect(Collectors.joining("\n")));
                c.setMsOcr((int) r.msTotal);
                c.setEstado(CapturaCupon.LISTO);
                c.setUsadoEn(LocalDateTime.now());   // el token se consume RECIEN con un resultado bueno
            }
        } catch (Exception e) {
            log.error("fallo el OCR de la captura {}", c.getId(), e);
            c.setEstado(CapturaCupon.ERROR);
            c.setError(recortar(e.getMessage()));
        }
        return repository.save(c);
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
