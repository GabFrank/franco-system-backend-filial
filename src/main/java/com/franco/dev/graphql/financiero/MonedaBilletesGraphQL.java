package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.MonedaBilletes;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.MonedaBilletesInput;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.MonedaBilleteService;
import com.franco.dev.service.financiero.MonedaService;
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
public class MonedaBilletesGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MonedaBilleteService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MonedaService monedaService;

    public Optional<MonedaBilletes> monedaBillete(Long id) {return service.findById(id);}

    public List<MonedaBilletes> monedaBilleteList(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public MonedaBilletes saveMonedaBillete(MonedaBilletesInput input){
        ModelMapper m = new ModelMapper();
        MonedaBilletes e = m.map(input, MonedaBilletes.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getMonedaId()!=null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        return service.save(e);
    }

    public List<MonedaBilletes> monedaBilletePorMonedaId(Long id){
        return service.findByMonedaId(id);
    }

    public Boolean deleteMonedaBillete(Long id){
        return service.deleteById(id);
    }

    public Long countMonedaBillete(){
        return service.count();
    }


}
