package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Sencillo;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.SencilloInput;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.SencilloService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.FuncionarioService;
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
public class SencilloGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SencilloService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    public Optional<Sencillo> sencillo(Long id) {return service.findById(id);}

    public List<Sencillo> sencillos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public Sencillo saveSencillo(SencilloInput input){
        ModelMapper m = new ModelMapper();
        Sencillo e = m.map(input, Sencillo.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getAutorizadoPorId()!=null) e.setAutorizadoPor(funcionarioService.findById(input.getAutorizadoPorId()).orElse(null));
        return service.save(e);
    }

//    public List<Sencillo> sencillosSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteSencillo(Long id){
        return service.deleteById(id);
    }

    public Long countSencillo(){
        return service.count();
    }


}
