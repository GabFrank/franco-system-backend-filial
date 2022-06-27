package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.RetiroDetalleInput;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.general.PaisService;
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
public class RetiroDetalleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RetiroDetalleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private RetiroService retiroService;

    @Autowired
    private MonedaService monedaService;

    public Optional<RetiroDetalle> retiroDetalle(Long id) {return service.findById(id);}

    public List<RetiroDetalle> retiroDetalles(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public RetiroDetalle saveRetiroDetalle(RetiroDetalleInput input){
        ModelMapper m = new ModelMapper();
        RetiroDetalle e = m.map(input, RetiroDetalle.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getMonedaId()!=null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if(input.getRetiroId()!=null) e.setRetiro(retiroService.findById(input.getRetiroId()).orElse(null));
        return service.save(e);
    }

    public List<RetiroDetalle> retiroDetalleListPorRetiroId(Long id){
        return service.findByRetiroId(id);
    }

    public Boolean deleteRetiroDetalle(Long id){
        return service.deleteById(id);
    }

    public Long countRetiroDetalle(){
        return service.count();
    }


}
