package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.graphql.financiero.input.VentaTarjetaInput;
import com.franco.dev.service.financiero.TerminalPosService;
import com.franco.dev.service.financiero.VentaTarjetaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(entity);
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
