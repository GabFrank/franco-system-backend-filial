package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.graphql.financiero.MovimientoCajaGraphQL;
import com.franco.dev.graphql.financiero.input.MovimientoCajaInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.operaciones.CompraService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class CobroDetalleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CobroDetalleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MonedaService monedaService;
    @Autowired
    private FormaPagoService formaPagoService;
    @Autowired
    private CobroService cobroService;
    @Autowired
    private MovimientoCajaGraphQL movimientoCajaGraphQL;


    @Autowired
    private CompraService compraService;

    public Optional<CobroDetalle> cobroDetalle(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<CobroDetalle> cobroDetallePorCobroId(Long id, Long sucId) {
        return service.findByCobroId(id);
    }

    public List<CobroDetalle> cobroDetalleList(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public CobroDetalle saveCobroDetalle(CobroDetalleInput input) {
        ModelMapper m = new ModelMapper();
        CobroDetalle e = m.map(input, CobroDetalle.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getMonedaId() != null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if (input.getFormaPagoId() != null)
            e.setFormaPago(formaPagoService.findById(input.getFormaPagoId()).orElse(null));
        if (input.getCobroId() != null) e.setCobro(cobroService.findById(input.getCobroId()).orElse(null));
        CobroDetalle cobroDetalle = service.saveAndSend(e, false);
        return cobroDetalle;
    }

    public List<CobroDetalle> saveCobroDetalleList(List<CobroDetalleInput> cobroDetalleInputList) {
        List<CobroDetalle> cobroDetalleList = new ArrayList<>();
        if (cobroDetalleInputList != null) {
            for (CobroDetalleInput input : cobroDetalleInputList) {
                CobroDetalle cobroDetalle = saveCobroDetalle(input);
                cobroDetalleList.add(cobroDetalle);
                if (input.getId() == null && cobroDetalle.getDescuento() != true && cobroDetalle.getAumento() != true && cobroDetalle.getFormaPago().getDescripcion().toUpperCase().contains("EFECTIVO")) {
                    MovimientoCajaInput movimientoCajaInput = new MovimientoCajaInput();
                    movimientoCajaInput.setMonedaId(cobroDetalle.getMoneda().getId());
                    movimientoCajaInput.setCantidad(cobroDetalle.getValor());
                    movimientoCajaInput.setActivo(true);
                    movimientoCajaInput.setPdvCajaId(cobroDetalle.getSucursalId());
                    movimientoCajaInput.setReferencia(cobroDetalle.getCobro().getId());
                    movimientoCajaInput.setTipoMovimiento(PdvCajaTipoMovimiento.VENTA);
                    movimientoCajaInput.setUsuarioId(cobroDetalle.getCobro().getUsuario().getId());
                    movimientoCajaGraphQL.saveMovimientoCaja(movimientoCajaInput);
                }
            }
        }
        return cobroDetalleList;
    }

    public Boolean deleteCobroDetalle(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countCobroDetalle() {
        return service.count();
    }


}
