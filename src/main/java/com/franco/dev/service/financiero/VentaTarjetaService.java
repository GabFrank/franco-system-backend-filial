package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.repository.financiero.CapturaCuponRepository;
import com.franco.dev.repository.financiero.VentaTarjetaRepository;
import com.franco.dev.repository.operaciones.CobroDetalleRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class VentaTarjetaService extends CrudService<VentaTarjeta, VentaTarjetaRepository> {

    private static final Logger log = LoggerFactory.getLogger(VentaTarjetaService.class);

    private final VentaTarjetaRepository repository;

    private final CobroDetalleRepository cobroDetalleRepository;

    private final MonedaService monedaService;

    /** De aca sale la ventana del chequeo de duplicado por codigo de autorizacion. */
    private final ConfiguracionVentaTarjetaService configuracionService;

    /** Para copiar la ruta de la foto a la venta, y que deje de ser un archivo huerfano. */
    private final CapturaCuponRepository capturas;

    @Override
    public VentaTarjetaRepository getRepository() {
        return repository;
    }

    public VentaTarjeta findByIdAndSucursalId(Long id, Long sucursalId) {
        return repository.findByIdAndSucursalId(id, sucursalId);
    }

    public VentaTarjeta findByVentaIdAndSucursalId(Long ventaId, Long sucursalId) {
        VentaTarjeta pendiente = repository.findFirstByVentaIdAndSucursalIdAndEstadoOrderByIdAsc(ventaId, sucursalId, "PENDIENTE");
        return pendiente != null ? pendiente : repository.findFirstByVentaIdAndSucursalIdOrderByIdAsc(ventaId, sucursalId);
    }

    public List<VentaTarjeta> findAllByVentaIdAndSucursalId(Long ventaId, Long sucursalId) {
        return repository.findByVentaIdAndSucursalIdOrderByIdAsc(ventaId, sucursalId);
    }

    public List<VentaTarjeta> findByCajaIdAndSucursalId(Long cajaId, Long sucursalId) {
        return repository.findByCajaIdAndSucursalIdOrderByCreadoEnDesc(cajaId, sucursalId);
    }

    public Long countVentasTarjetaSinRegistrar(Long cajaId, Long sucursalId) {
        return repository.countByCajaIdAndSucursalIdAndEstado(cajaId, sucursalId, "PENDIENTE");
    }

    /**
     * Completa un registro leyendo el QR impreso por el POS, sin pasar por el celular.
     * <p>
     * NO reusa save(): el resolver arma la entidad de cero a partir del input y dejaria en
     * null ventaId, cajaId, monto, terminalPos y usuario, que son justamente los campos que
     * el PDV ya cargo al crear el PENDIENTE.
     * <p>
     * Solo se puede completar un PENDIENTE. Los otros tres estados son terminales:
     * COMPLETADO ya tiene datos (pisarlos borraria una registracion buena), CANCELADO y
     * NO_COMPLETADO son decisiones tomadas — NO_COMPLETADO lo escribe el cierre de caja.
     * Sin esta validacion, escanear dos veces el mismo cupon sobreescribe en silencio.
     *
     * @param identificadorTransaccion referencia unica del proveedor (el EndToEndId en Pix).
     *                                 Se copia ademas al CobroDetalle de la venta, que es
     *                                 donde vive la conciliacion.
     * @param origen                   QR | OCR | MANUAL | API. <b>Lo manda el cliente</b>, porque
     *                                 es el unico que sabe por que camino obtuvo los datos: el
     *                                 backend ve exactamente el mismo `completar` en los cuatro
     *                                 casos. Si no viene, se deduce QR cuando hay qrCrudo --que
     *                                 solo existe si entro por el lector-- y en cualquier otro
     *                                 caso queda NULL, que dice "no se sabe" en vez de mentir.
     *                                 <p>
     *                                 Este es el UNICO metodo que pasa una venta_tarjeta a
     *                                 COMPLETADO, en los dos backends: por eso el origen se
     *                                 escribe aca y no en central, que solo tiene un update
     *                                 generico usado para otras cosas.
     */
    @Transactional
    public VentaTarjeta completar(Long id,
                                  Long sucursalId,
                                  String codigoAutorizacion,
                                  String numeroBoleta,
                                  BigDecimal montoEscaneado,
                                  String identificadorTransaccion,
                                  String qrCrudo,
                                  Long cobroDetalleId,
                                  Long monedaId,
                                  String origen,
                                  String capturaToken,
                                  String datosExtra) {
        VentaTarjeta vt = repository.findByIdAndSucursalId(id, sucursalId);
        if (vt == null) {
            throw new GraphQLException("No existe la venta con tarjeta " + id + " en la sucursal " + sucursalId);
        }
        if (!"PENDIENTE".equals(vt.getEstado())) {
            throw new GraphQLException("La venta con tarjeta " + id + " esta en estado " + vt.getEstado()
                    + ". Solo se puede completar una PENDIENTE.");
        }

        validarCuponNoUsado(vt, identificadorTransaccion, qrCrudo, codigoAutorizacion, montoEscaneado);
        validarMoneda(vt, monedaId, cobroDetalleId);

        // Si el PENDIENTE se creo sin moneda (cliente viejo), el cupon la aporta ahora. Ya paso
        // por validarMoneda(), asi que llegar aca significa que coincide con la del cobro.
        if (monedaId != null && vt.getMoneda() == null) {
            monedaService.findById(monedaId).ifPresent(vt::setMoneda);
        }

        vt.setCodigoAutorizacion(codigoAutorizacion);
        vt.setNumeroBoleta(numeroBoleta);
        vt.setMontoEscaneado(montoEscaneado);
        vt.setQrCrudo(qrCrudo);
        vt.setOrigen(origenEfectivo(origen, qrCrudo));
        // Lo que el cupon trae y no tiene columna propia. Se pisa solo si viene: completar se
        // puede llamar de nuevo sobre el mismo registro, y un segundo intento sin datos extra no
        // tiene por que borrar los del primero.
        if (datosExtra != null && !datosExtra.trim().isEmpty()) {
            vt.setDatosExtra(datosExtra);
        }
        vt.setEstado("COMPLETADO");
        // La foto pasa a ser evidencia del cobro, no un subproducto del OCR. venta_tarjeta.
        // imagen_url ya existia y estaba muerta: nadie la llenaba en este flujo. Sin esto la
        // imagen queda colgando en captura_cupon sin ninguna relacion con la venta --no hay FK ni
        // columna-- y el job de purga no puede distinguir una foto huerfana de la evidencia de un
        // cobro que manana se discute.
        if (capturaToken != null && !capturaToken.trim().isEmpty()) {
            capturas.findByToken(capturaToken.trim())
                    .filter(c -> c.getImagenUrl() != null)
                    .ifPresent(c -> vt.setImagenUrl(c.getImagenUrl()));
        }
        VentaTarjeta guardado = repository.save(vt);

        vincularIdentificadorAlCobro(vt, identificadorTransaccion, cobroDetalleId);
        return guardado;
    }

    /**
     * La moneda del cupon tiene que ser la del cobro que paga.
     * <p>
     * No es una advertencia: `monto` y `monto_escaneado` se guardan sin unidad, asi que un cupon
     * de 8.000 R$ contra un cobro de 8.000 Gs da diferencia CERO en cualquier reporte de
     * conciliacion — el error queda invisible, y son ~5900x. Verificado en la prueba manual del
     * 2026-09-04: se registro sin un solo aviso.
     * <p>
     * La referencia es el COBRO, no la terminal: es el cobro el que se esta pagando. Si no se
     * sabe cual es el cobro todavia, se usa la moneda de la terminal como aproximacion.
     */
    private void validarMoneda(VentaTarjeta vt, Long monedaId, Long cobroDetalleId) {
        if (monedaId == null) return;

        Long monedaDelCobro = null;
        if (cobroDetalleId != null) {
            monedaDelCobro = cobroDetalleRepository
                    .findByVentaIdAndSucursalId(vt.getVentaId(), vt.getSucursalId()).stream()
                    .filter(cd -> cobroDetalleId.equals(cd.getId()))
                    .findFirst()
                    .map(cd -> cd.getMoneda() != null ? cd.getMoneda().getId() : null)
                    .orElse(null);
        }
        if (monedaDelCobro == null && vt.getTerminalPos() != null
                && vt.getTerminalPos().getMoneda() != null) {
            monedaDelCobro = vt.getTerminalPos().getMoneda().getId();
        }
        if (monedaDelCobro == null) return;

        if (!monedaDelCobro.equals(monedaId)) {
            throw new GraphQLException("El cupon esta en otra moneda que el cobro. Un cupon en otra"
                    + " moneda no paga este cobro.");
        }
    }

    /**
     * Un cupon del POS corresponde a UN cobro y solo uno. Registrar el mismo dos veces imputa la
     * misma plata a dos ventas distintas, que es un descuadre real de caja, no una molestia.
     * <p>
     * Se bloquea y no se avisa: a diferencia del monto que no coincide (donde el cliente ya pago y
     * negarse empeora las cosas), un cupon repetido es un error objetivo. Si de verdad son dos
     * cobros distintos, van a tener referencias distintas.
     * <p>
     * Vive en el backend a proposito, para que no dependa de que el cliente se acuerde de
     * chequear.
     * <p>
     * <b>Cubre los dos caminos vivos: el lector del PDV y la foto del cupon.</b> La captura por
     * camara NO pasa por la app `mobile` --el telefono abre, en su navegador, una pagina que sirve
     * este mismo filial-- asi que los dos terminan aca.
     * <p>
     * Una version anterior de este comentario decia que "el desktop y el celular completan por el
     * mismo camino" refiriendose a la app, y eso era falso: la pantalla de venta con tarjeta de
     * `mobile` llama `updateVentaTarjeta` del CENTRAL, un setter generico sin validacion alguna.
     * Pero esa pantalla quedo fuera del circuito de la fase 2 y la app esta en mantenimiento
     * (la reemplaza `mobile-pwa`), asi que es un camino heredado, no un agujero de este flujo. Si
     * algun dia se reactiva, hay que hacerla pasar por aca.
     */
    private void validarCuponNoUsado(VentaTarjeta vt, String identificadorTransaccion, String qrCrudo,
                                     String codigoAutorizacion, BigDecimal montoEscaneado) {
        motivoCuponNoUsable(vt.getId(), vt.getVentaId(), vt.getSucursalId(), identificadorTransaccion,
                qrCrudo, codigoAutorizacion, montoEscaneado,
                vt.getTerminalPos() != null ? vt.getTerminalPos().getId() : null)
                .ifPresent(motivo -> {
                    throw new GraphQLException(motivo);
                });
    }

    /**
     * De donde salieron los datos, cuando el cliente no lo dice.
     * <p>
     * Solo se deduce el caso que el backend PUEDE saber: si hay qrCrudo, entro por el lector. OCR
     * y MANUAL son indistinguibles desde aca --los dos llegan como campos sueltos-- asi que se
     * dejan en NULL antes que adivinar. Un 'OCR' inventado sobre una carga a mano haria que la
     * conciliacion confie en un dato que un humano tipeo.
     */
    private static String origenEfectivo(String origen, String qrCrudo) {
        if (origen != null && !origen.trim().isEmpty()) {
            String limpio = origen.trim().toUpperCase();
            if (!VentaTarjeta.ORIGENES.contains(limpio)) {
                // Se valida ACA y no se deja llegar a la base a proposito. La columna tiene un
                // CHECK, pero una violacion de CHECK sube como DataIntegrityViolationException, y
                // el unico @ExceptionHandler del filial atrapa solo GraphQLException: el cajero
                // veria un error opaco de graphql-java sin ninguna pista de que fallo.
                throw new GraphQLException("Origen '" + origen.trim() + "' desconocido. Los validos"
                        + " son: " + String.join(", ", VentaTarjeta.ORIGENES) + ".");
            }
            return limpio;
        }
        if (qrCrudo != null && !qrCrudo.trim().isEmpty()) return VentaTarjeta.ORIGEN_QR;
        return null;
    }

    /**
     * El motivo por el que este cupon NO se puede usar, o vacio si esta libre.
     * <p>
     * Es la misma logica que corre al guardar, extraida para que el PDV pueda preguntar ANTES de
     * escanear en firme. Detectar el duplicado recien al guardar deja al cajero enterandose cuando
     * la venta ya se registro y el dato escaneado ya se descarto: el cupon se pierde y hay que
     * volver a registrarlo desde la lista. Preguntar antes lo corta con el ticket todavia en la
     * mano.
     * <p>
     * <b>Una sola fuente de verdad a proposito.</b> Si el chequeo previo fuera una copia, las dos
     * versiones divergirian y el PDV terminaria dejando pasar casos que el guardado rechaza —
     * exactamente el problema que se quiere evitar.
     *
     * @param ventaTarjetaId el registro que se esta completando, o {@code null} si todavia no
     *                       existe (pre-chequeo desde el PDV). Se excluye de la busqueda para que
     *                       un registro no choque consigo mismo.
     * @param ventaId        la venta duenha de los cobros que NO cuentan como ajenos, o
     *                       {@code null} en el pre-chequeo (ahi no hay venta todavia).
     */
    /**
     * Ventas con tarjeta de una caja, paginadas y filtradas. Ver
     * {@link com.franco.dev.repository.financiero.VentaTarjetaRepository#filtrarPorCaja}.
     */
    public Page<VentaTarjeta> filtrarPorCaja(Long cajaId, Long sucursalId, String estado,
                                             Long terminalPosId, Long monedaId,
                                             BigDecimal montoDesde, BigDecimal montoHasta,
                                             Long usuarioId, int page, int size) {
        return repository.filtrarPorCaja(cajaId, sucursalId, estado, terminalPosId, monedaId,
                montoDesde, montoHasta, usuarioId, PageRequest.of(page, size));
    }

    /**
     * El motivo por el que este cupon no se puede usar, o vacio si esta libre.
     * <p>
     * Tres chequeos, de mas fuerte a mas debil:
     * <ol>
     *   <li>{@code qrCrudo} — la cadena entera que imprimio el POS. Solo existe si entro por el
     *       lector.</li>
     *   <li>{@code identificadorTransaccion} — referencia propia del proveedor. Solo la llenan los
     *       formatos que traen un campo aparte: el EndToEndId de Pix si; Dinelco, Infonet, Stone,
     *       BXX y PlugPay no.</li>
     *   <li>{@code codigoAutorizacion} + terminal + ventana — el unico que la <b>carga a mano</b>
     *       garantiza para todos los proveedores.</li>
     * </ol>
     * El tercero se agrego junto con la carga a mano, y no es opcional: sin el, un cajero puede
     * tipear el cupon de la venta anterior entero --que quedo sobre el mostrador-- y producir un
     * registro valido y falso que nadie descubre hasta la conciliacion. Es el caso 5 del analisis
     * de modos de falla.
     */
    public Optional<String> motivoCuponNoUsable(Long ventaTarjetaId, Long ventaId, Long sucursalId,
                                                String identificadorTransaccion, String qrCrudo,
                                                String codigoAutorizacion, BigDecimal montoEscaneado,
                                                Long terminalPosId) {
        if (qrCrudo != null && !qrCrudo.trim().isEmpty()) {
            List<VentaTarjeta> previos = repository.findByQrCrudo(qrCrudo.trim());
            if (previos != null) {
                for (VentaTarjeta otro : previos) {
                    if (otro.getId() != null && !otro.getId().equals(ventaTarjetaId)) {
                        return Optional.of("Ese cupon ya fue registrado en la venta con tarjeta "
                                + otro.getId() + " (venta " + otro.getVentaId() + "). Un cupon no se puede"
                                + " usar en dos cobros.");
                    }
                }
            }
        }

        if (identificadorTransaccion != null && !identificadorTransaccion.trim().isEmpty()) {
            List<CobroDetalle> usados = cobroDetalleRepository
                    .findByIdentificadorTransaccion(identificadorTransaccion.trim());
            if (usados != null) {
                // Los cobros de ESTA venta no cuentan: el PDV escribe el identificador junto con el
                // saveVenta, asi que al completar ya esta puesto en la linea correcta. En el
                // pre-chequeo no hay venta todavia, asi que no hay nada propio que excluir.
                List<Long> propios = ventaId == null
                        ? Collections.emptyList()
                        : cobroDetalleRepository.findByVentaIdAndSucursalId(ventaId, sucursalId)
                                .stream().map(CobroDetalle::getId).collect(Collectors.toList());
                for (CobroDetalle cd : usados) {
                    if (cd.getId() != null && !propios.contains(cd.getId())) {
                        return Optional.of("Esa referencia (" + identificadorTransaccion.trim()
                                + ") ya esta registrada en el cobro " + cd.getId() + " de otra venta.");
                    }
                }
            }
        }

        Optional<String> porCodigo = motivoPorCodigoAutorizacion(
                ventaTarjetaId, sucursalId, codigoAutorizacion, montoEscaneado, terminalPosId);
        if (porCodigo.isPresent()) return porCodigo;

        return Optional.empty();
    }

    /**
     * El mismo codigo de autorizacion, en el mismo aparato, por el mismo monto, dentro de la
     * ventana configurada.
     * <p>
     * <b>Los tres acotamientos existen para no bloquear ventas buenas</b>, que seria peor que el
     * problema que evitan --el cajero tiene al cliente adelante y el cobro ya paso:
     * <ul>
     *   <li><b>Terminal</b>: el codigo lo emite el aparato y solo es unico ahi. Dos maquinitas
     *       distintas pueden emitir el mismo numero el mismo dia sin que eso signifique nada.</li>
     *   <li><b>Ventana</b>: varios proveedores usan codigos cortos --4 a 6 caracteres-- que se
     *       reciclan. Sin ventana, un local con movimiento chocaria consigo mismo en semanas.</li>
     *   <li><b>Monto</b>: es lo que separa un cupon REPETIDO de una colision de codigo corto. Un
     *       cupon retipeado repite todo, monto incluido; una colision casi nunca coincide en el
     *       monto. Si a alguno de los dos le falta el monto, no se usa para descartar: se prefiere
     *       avisar de mas antes que dejar pasar un duplicado real.</li>
     * </ul>
     * Solo mira COMPLETADOS: un PENDIENTE todavia no imputo nada.
     */
    private Optional<String> motivoPorCodigoAutorizacion(Long ventaTarjetaId, Long sucursalId,
                                                         String codigoAutorizacion,
                                                         BigDecimal montoEscaneado,
                                                         Long terminalPosId) {
        if (codigoAutorizacion == null || codigoAutorizacion.trim().isEmpty()) return Optional.empty();
        if (sucursalId == null) return Optional.empty();

        int horas = configuracionService.findOrDefault().horasVentanaDuplicadoEfectivo();
        LocalDateTime desde = LocalDateTime.now().minusHours(horas);

        List<VentaTarjeta> previos = repository.buscarPorCodigoAutorizacion(
                sucursalId, codigoAutorizacion.trim(), terminalPosId, desde);
        if (previos == null) return Optional.empty();

        for (VentaTarjeta otro : previos) {
            if (otro.getId() == null || otro.getId().equals(ventaTarjetaId)) continue;
            if (montosDistintos(montoEscaneado, otro.getMontoEscaneado())) continue;

            return Optional.of("El codigo de autorizacion " + codigoAutorizacion.trim()
                    + " ya esta registrado en la venta con tarjeta " + otro.getId()
                    + " (venta " + otro.getVentaId() + "), hace menos de " + horas
                    + " h y en la misma terminal. Un cupon no se puede usar en dos cobros.");
        }
        return Optional.empty();
    }

    /**
     * Dos montos que se sabe que son distintos. Si a alguno le falta el dato, la respuesta es
     * `false`: no alcanza para descartar un duplicado, y el chequeo sigue.
     */
    private static boolean montosDistintos(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return false;
        return a.compareTo(b) != 0;
    }

    /**
     * Copia la referencia del proveedor al CobroDetalle de TARJETA de la venta.
     * <p>
     * Tres caminos, en este orden:
     * <ol>
     *   <li><b>cobroDetalleId explicito</b> — el usuario eligio la linea. Manda sobre todo lo
     *       demas y sus errores SI fallan hacia afuera.</li>
     *   <li><b>Ya vinculado</b> — el PDV mando el identificador en el CobroDetalleInput de la
     *       linea escaneada, asi que el vinculo vino con el saveVenta. No se toca nada.</li>
     *   <li><b>Inferencia por monto</b> — best-effort para los casos viejos. Si quedan dos
     *       candidatos del mismo monto NO se escribe nada: la referencia igual esta en
     *       venta_tarjeta.qr_crudo, y es preferible a colgarsela al cobro equivocado.</li>
     * </ol>
     * La inferencia nunca hace fallar la completacion: el registro de la tarjeta es el dato que
     * el cajero espera; el vinculo con el cobro es para conciliar despues.
     */
    private void vincularIdentificadorAlCobro(VentaTarjeta vt, String identificadorTransaccion,
                                              Long cobroDetalleId) {
        if (identificadorTransaccion == null || identificadorTransaccion.trim().isEmpty()) return;
        try {
            List<CobroDetalle> tarjetas = cobroDetalleRepository
                    .findByVentaIdAndSucursalId(vt.getVentaId(), vt.getSucursalId()).stream()
                    .filter(cd -> cd.getFormaPago() != null && "TARJETA".equals(cd.getFormaPago().getDescripcion()))
                    .filter(cd -> Boolean.TRUE.equals(cd.getPago()))
                    .collect(Collectors.toList());
            if (tarjetas.isEmpty()) return;

            // El usuario eligio la linea: manda, no se infiere nada. Este es el unico camino
            // posible cuando hay dos cobros con tarjeta del mismo monto.
            if (cobroDetalleId != null) {
                CobroDetalle elegido = tarjetas.stream()
                        .filter(cd -> cobroDetalleId.equals(cd.getId()))
                        .findFirst()
                        .orElseThrow(() -> new GraphQLException(
                                "El cobro " + cobroDetalleId + " no es un cobro con tarjeta de la venta "
                                        + vt.getVentaId() + "."));
                String yaTiene = elegido.getIdentificadorTransaccion();
                if (yaTiene != null && !yaTiene.trim().isEmpty()
                        && !yaTiene.equals(identificadorTransaccion)) {
                    throw new GraphQLException("El cobro " + cobroDetalleId
                            + " ya esta vinculado a otro cupon (" + yaTiene + ").");
                }
                elegido.setIdentificadorTransaccion(identificadorTransaccion);
                cobroDetalleRepository.save(elegido);
                return;
            }

            // Ya vinculado: el PDV manda el identificador en el CobroDetalleInput de la linea
            // escaneada, asi que cuando la venta se guarda el vinculo ya viene hecho y es EXACTO.
            // Sin este corte, la inferencia de abajo veria dos candidatos del mismo monto,
            // descartaria la que ya tiene identificador y le colgaria este a la OTRA linea: un
            // vinculo incorrecto, peor que no tener ninguno.
            boolean yaVinculado = tarjetas.stream()
                    .anyMatch(cd -> identificadorTransaccion.equals(cd.getIdentificadorTransaccion()));
            if (yaVinculado) return;

            List<CobroDetalle> candidatos = tarjetas;
            if (candidatos.size() > 1 && vt.getMonto() != null) {
                List<CobroDetalle> porMonto = candidatos.stream()
                        .filter(cd -> cd.getValor() != null
                                && BigDecimal.valueOf(cd.getValor()).compareTo(vt.getMonto()) == 0)
                        .collect(Collectors.toList());
                if (!porMonto.isEmpty()) candidatos = porMonto;
            }
            if (candidatos.size() > 1) {
                candidatos = candidatos.stream()
                        .filter(cd -> cd.getIdentificadorTransaccion() == null
                                || cd.getIdentificadorTransaccion().trim().isEmpty())
                        .collect(Collectors.toList());
            }
            if (candidatos.size() != 1) {
                log.warn("VentaTarjeta {}: {} CobroDetalle de TARJETA candidatos, no se vincula el identificador",
                        vt.getId(), candidatos.size());
                return;
            }

            CobroDetalle destino = candidatos.get(0);
            destino.setIdentificadorTransaccion(identificadorTransaccion);
            cobroDetalleRepository.save(destino);
        } catch (GraphQLException e) {
            // La eleccion explicita del usuario SI tiene que fallar hacia afuera: eligio mal, o
            // el cobro ya estaba tomado por otro cupon. Tragarse eso dejaria al cajero creyendo
            // que vinculo algo que no vinculo. Solo la inferencia automatica es best-effort.
            throw e;
        } catch (Exception e) {
            log.warn("VentaTarjeta {}: no se pudo vincular el identificador al cobro: {}",
                    vt.getId(), e.getMessage());
        }
    }

    /**
     * Cierre de caja con pendientes confirmado por el cajero: los registros
     * PENDIENTE de la caja pasan a NO_COMPLETADO (estado terminal, auditable).
     * El cambio replica al central via BRANCH_TO_MAIN.
     */
    public int marcarNoCompletadas(Long cajaId, Long sucursalId) {
        List<VentaTarjeta> pendientes = repository.findByCajaIdAndSucursalIdAndEstado(cajaId, sucursalId, "PENDIENTE");
        pendientes.forEach(vt -> {
            vt.setEstado("NO_COMPLETADO");
            repository.save(vt);
        });
        return pendientes.size();
    }
}
