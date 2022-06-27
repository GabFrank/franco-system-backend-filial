package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.MonedaInput;
import com.franco.dev.service.financiero.BancoService;
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
public class BancoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private BancoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    public Optional<Banco> banco(Long id) {return service.findById(id);}

    public List<Banco> bancos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public Banco saveBanco(BancoInput input){
        ModelMapper m = new ModelMapper();
        Banco e = m.map(input, Banco.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(e);
    }

    public List<Banco> bancosSearch(String texto){
        return service.findByAll(texto);
    }

    public Boolean deleteBanco(Long id){
        return service.deleteById(id);
    }

    public Long countBanco(){
        return service.count();
    }


}
