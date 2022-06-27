package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.GastoDetalle;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.GastoDetalleInput;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
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
public class GastoDetalleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private GastoDetalleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private GastoService gastoService;

    public Optional<GastoDetalle> gastoDetalle(Long id) {return service.findById(id);}

    public List<GastoDetalle> gastoDetalles(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public GastoDetalle saveGastoDetalle(GastoDetalleInput input) throws GraphQLException  {
        ModelMapper m = new ModelMapper();
        GastoDetalle e = m.map(input, GastoDetalle.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getMonedaId()!=null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if(input.getGastoId()!=null) e.setGasto(gastoService.findById(input.getGastoId()).orElse(null));
        if(input.getCantidad()>0){
            return service.save(e);
        } else {
            return null;
        }
    }

    public List<GastoDetalle> gastoDetalleListPorGastoId(Long id){
        return service.findByGastoId(id);
    }

    public Boolean deleteGastoDetalle(Long id){
        return service.deleteById(id);
    }

    public Long countGastoDetalle(){
        return service.count();
    }


}
