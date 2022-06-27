package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.ConteoMonedaService;
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
public class ConteoMonedaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ConteoMonedaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    public Optional<ConteoMoneda> conteoMoneda(Long id) {return service.findById(id);}

    public List<ConteoMoneda> conteoMonedas(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public ConteoMoneda saveConteoMoneda(ConteoMonedaInput input){
        ModelMapper m = new ModelMapper();
        ConteoMoneda e = m.map(input, ConteoMoneda.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(e);
    }

    public List<ConteoMoneda> conteoMonedasPorConteoId(Long id){
        return service.findByConteoId(id);
    }

//    public List<ConteoMoneda> conteoMonedasSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteConteoMoneda(Long id){
        return service.deleteById(id);
    }

    public Long countConteoMoneda(){
        return service.count();
    }


}
