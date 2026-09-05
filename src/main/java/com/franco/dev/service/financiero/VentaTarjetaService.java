package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.repository.financiero.VentaTarjetaRepository;
import com.franco.dev.repository.operaciones.CobroDetalleRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class VentaTarjetaService extends CrudService<VentaTarjeta, VentaTarjetaRepository> {

    private static final Logger log = LoggerFactory.getLogger(VentaTarjetaService.class);

    private final VentaTarjetaRepository repository;

    private final CobroDetalleRepository cobroDetalleRepository;

    private final MonedaService monedaService;

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
                                  Long monedaId) {
        VentaTarjeta vt = repository.findByIdAndSucursalId(id, sucursalId);
        if (vt == null) {
            throw new GraphQLException("No existe la venta con tarjeta " + id + " en la sucursal " + sucursalId);
        }
        if (!"PENDIENTE".equals(vt.getEstado())) {
            throw new GraphQLException("La venta con tarjeta " + id + " esta en estado " + vt.getEstado()
                    + ". Solo se puede completar una PENDIENTE.");
        }

        validarCuponNoUsado(vt, identificadorTransaccion, qrCrudo);
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
        vt.setEstado("COMPLETADO");
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
     * Vive en el backend a proposito: el desktop y el celular completan por el mismo camino, y la
     * validacion tiene que valer para los dos.
     */
    private void validarCuponNoUsado(VentaTarjeta vt, String identificadorTransaccion, String qrCrudo) {
        if (qrCrudo != null && !qrCrudo.trim().isEmpty()) {
            List<VentaTarjeta> previos = repository.findByQrCrudo(qrCrudo.trim());
            if (previos != null) {
                for (VentaTarjeta otro : previos) {
                    if (otro.getId() != null && !otro.getId().equals(vt.getId())) {
                        throw new GraphQLException("Ese cupon ya fue registrado en la venta con tarjeta "
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
                // saveVenta, asi que al completar ya esta puesto en la linea correcta.
                List<Long> propios = cobroDetalleRepository
                        .findByVentaIdAndSucursalId(vt.getVentaId(), vt.getSucursalId())
                        .stream().map(CobroDetalle::getId).collect(Collectors.toList());
                for (CobroDetalle cd : usados) {
                    if (cd.getId() != null && !propios.contains(cd.getId())) {
                        throw new GraphQLException("Esa referencia (" + identificadorTransaccion.trim()
                                + ") ya esta registrada en el cobro " + cd.getId() + " de otra venta.");
                    }
                }
            }
        }
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
