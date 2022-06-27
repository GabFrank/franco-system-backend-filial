package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.SencilloDetalle;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.SencilloDetalleInput;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.SencilloDetalleService;
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
public class SencilloDetalleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SencilloDetalleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CambioService cambioService;

    public Optional<SencilloDetalle> sencilloDetalle(Long id) {return service.findById(id);}

    public List<SencilloDetalle> sencilloDetalles(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public SencilloDetalle saveSencilloDetalle(SencilloDetalleInput input){
        ModelMapper m = new ModelMapper();
        SencilloDetalle e = m.map(input, SencilloDetalle.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getCambioId()!=null) e.setCambio(cambioService.findById(input.getCambioId()).orElse(null));
        return service.save(e);
    }

    public List<SencilloDetalle> sencilloDetalleListPorSencilloId(Long id){
        return service.findBySencilloId(id);
    }

    public Boolean deleteSencilloDetalle(Long id){
        return service.deleteById(id);
    }

    public Long countSencilloDetalle(){
        return service.count();
    }


}
