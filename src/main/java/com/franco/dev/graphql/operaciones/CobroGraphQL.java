package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Compra;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.graphql.financiero.MovimientoCajaGraphQL;
import com.franco.dev.graphql.financiero.input.MovimientoCajaInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.CompraInput;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.operaciones.CompraService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CobroGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CobroService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private CobroDetalleGraphQL cobroDetalleGraphQL;

    @Autowired
    private MovimientoCajaGraphQL movimientoCajaGraphQL;

    public Optional<Cobro> cobro(Long id) {return service.findById(id);}

    public List<Cobro> cobros(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Cobro saveCobro(CobroInput input, List<CobroDetalleInput> cobroDetalleList, Long cajaId){
        ModelMapper m = new ModelMapper();
        Cobro e = m.map(input, Cobro.class);
        if(e.getUsuario()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        Cobro cobro = service.save(e);
        if(cobro!=null){
            for(CobroDetalleInput c : cobroDetalleList){
                c.setCobroId(cobro.getId());
                CobroDetalle cobroDetalle = cobroDetalleGraphQL.saveCobroDetalle(c);
                if(cobroDetalle.getDescuento()!=true && cobroDetalle.getFormaPago().getDescripcion().toUpperCase().contains("EFECTIVO")){
                    MovimientoCajaInput movimientoCajaInput = new MovimientoCajaInput();
                    movimientoCajaInput.setMonedaId(c.getMonedaId());
                    movimientoCajaInput.setCantidad(c.getValor());
                    movimientoCajaInput.setActivo(true);
                    movimientoCajaInput.setPdvCajaId(cajaId);
                    movimientoCajaInput.setReferencia(cobro.getId());
                    movimientoCajaInput.setTipoMovimiento(PdvCajaTipoMovimiento.VENTA);
                    movimientoCajaInput.setUsuarioId(cobro.getUsuario().getId());
                    movimientoCajaGraphQL.saveMovimientoCaja(movimientoCajaInput);
                }

            }
            return cobro;
        } else {
            return null;
        }

    }

    public Boolean deleteCobro(Long id){
        List<CobroDetalle> cobroDetalleList = cobroDetalleGraphQL.cobroDetallePorCobroId(id);
        for(CobroDetalle c : cobroDetalleList){
            movimientoCajaGraphQL.desactivarByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento.VENTA, c.getId());
            return true;
        }
        return false;
    }

    public Long countCobro(){
        return service.count();
    }

}
