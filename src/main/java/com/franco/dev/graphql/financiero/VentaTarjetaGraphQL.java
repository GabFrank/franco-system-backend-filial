package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.graphql.financiero.input.CompletarVentaTarjetaInput;
import com.franco.dev.graphql.financiero.input.VentaTarjetaInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.TerminalPosService;
import com.franco.dev.service.financiero.VentaTarjetaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.util.List;

/**
 * VentaTarjeta en filial: el POS crea aca el registro PENDIENTE (funciona sin
 * internet) y la replicacion BRANCH_TO_MAIN lo sube al central, donde la app
 * movil lo completa. A diferencia del resolver del central, save no espera
 * sincronizacion de la venta: la venta vive en esta misma base.
 */
@Component
public class VentaTarjetaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VentaTarjetaService service;

    @Autowired
    private TerminalPosService terminalPosService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private UsuarioService usuarioService;

    public VentaTarjeta ventaTarjetaPorId(Long id, Long sucId) {
        return service.findByIdAndSucursalId(id, sucId);
    }

    public VentaTarjeta ventaTarjetaPorVentaId(Long ventaId, Long sucId) {
        return service.findByVentaIdAndSucursalId(ventaId, sucId);
    }

    public List<VentaTarjeta> ventasTarjetaPorCaja(Long cajaId, Long sucId) {
        return service.findByCajaIdAndSucursalId(cajaId, sucId);
    }

    /**
     * Pre-chequeo del cupon, para que el PDV pueda avisar ANTES de dar el escaneo por bueno.
     * Devuelve el motivo, o null si el cupon esta libre.
     * <p>
     * NO reemplaza la validacion del guardado: esa sigue corriendo igual y es la que manda. Esto
     * solo adelanta el aviso — entre el escaneo y el saveVenta puede pasar cualquier cosa, y
     * confiar en un chequeo del cliente seria un agujero.
     */
    public Page<VentaTarjeta> filtrarVentasTarjetaPorCaja(Long cajaId, Long sucId, String estado,
                                                         Long terminalPosId, Long monedaId,
                                                         Double montoDesde, Double montoHasta,
                                                         Integer page, Integer size) {
        return service.filtrarPorCaja(
                cajaId, sucId, estado, terminalPosId, monedaId,
                montoDesde != null ? BigDecimal.valueOf(montoDesde) : null,
                montoHasta != null ? BigDecimal.valueOf(montoHasta) : null,
                page != null ? page : 0,
                size != null ? size : 15);
    }

    /**
     * El pre-chequeo del cupon, antes de que el cajero de el dato por bueno.
     * <p>
     * `montoEscaneado` no se pide aca a proposito: en el pre-chequeo el cajero muchas veces
     * todavia no lo tiene --acaba de escanear o de tipear el codigo-- y sin monto el chequeo por
     * codigo de autorizacion avisa de mas, que es el lado correcto para equivocarse en una
     * advertencia. Al guardar, `completar` lo pasa y el filtro se afina.
     */
    public String motivoCuponNoUsable(String qrCrudo, String identificadorTransaccion,
                                      String codigoAutorizacion, Long terminalPosId, Long sucId) {
        return service.motivoCuponNoUsable(null, null, sucId, identificadorTransaccion, qrCrudo,
                        codigoAutorizacion, null, terminalPosId)
                .orElse(null);
    }

    public Long countVentasTarjetaSinRegistrar(Long cajaId, Long sucId) {
        Long count = service.countVentasTarjetaSinRegistrar(cajaId, sucId);
        return count != null ? count : 0L;
    }

    public VentaTarjeta saveVentaTarjeta(VentaTarjetaInput input) {
        VentaTarjeta entity = new VentaTarjeta();
        entity.setId(input.getId());
        entity.setSucursalId(input.getSucursalId());
        entity.setVentaId(input.getVentaId());
        entity.setCajaId(input.getCajaId());
        entity.setCodigoAutorizacion(input.getCodigoAutorizacion());
        entity.setNumeroBoleta(input.getNumeroBoleta());
        entity.setMonto(input.getMonto());
        entity.setMontoEscaneado(input.getMontoEscaneado());
        entity.setImagenUrl(input.getImagenUrl());
        entity.setEstado(input.getEstado() != null ? input.getEstado() : "PENDIENTE");
        if (input.getTerminalPosId() != null) {
            entity.setTerminalPos(terminalPosService.findById(input.getTerminalPosId()).orElse(null));
        }
        // La moneda del COBRO, no la de la terminal: es el cobro el que se esta pagando, y sin
        // ella monto/monto_escaneado quedan sin unidad.
        if (input.getMonedaId() != null) {
            entity.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(entity);
    }

    /**
     * Completa un PENDIENTE con los datos que vienen del QR impreso por el POS, leidos con el
     * lector del PDV. Es el reemplazo del camino foto+OCR desde el celular.
     * <p>
     * Deliberadamente NO reusa saveVentaTarjeta: ese arma la entidad de cero y dejaria en null
     * ventaId, cajaId, monto, terminalPos y usuario. Ver VentaTarjetaService#completar, que
     * ademas valida que el estado sea PENDIENTE (el updateVentaTarjeta del central no valida).
     */
    public VentaTarjeta completarVentaTarjeta(CompletarVentaTarjetaInput input) {
        return service.completar(
                input.getId(),
                input.getSucursalId(),
                input.getCodigoAutorizacion(),
                input.getNumeroBoleta(),
                input.getMontoEscaneado(),
                input.getIdentificadorTransaccion(),
                input.getQrCrudo(),
                input.getCobroDetalleId(),
                input.getMonedaId(),
                input.getOrigen());
    }

    public Boolean cancelarVentaTarjetaPorVentaId(Long ventaId, Long sucId) {
        List<VentaTarjeta> registros = service.findAllByVentaIdAndSucursalId(ventaId, sucId);
        if (registros == null || registros.isEmpty()) return false;
        registros.forEach(vt -> {
            vt.setEstado("CANCELADO");
            service.save(vt);
        });
        return true;
    }

    public Integer marcarVentasTarjetaNoCompletadas(Long cajaId, Long sucId) {
        return service.marcarNoCompletadas(cajaId, sucId);
    }
}
