package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.graphql.financiero.input.CambioInput;
import com.franco.dev.graphql.financiero.input.MonedaInput;
import com.franco.dev.service.financiero.CambioService;
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
public class CambioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CambioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MonedaService monedaService;

    public Optional<Cambio> cambio(Long id) {return service.findById(id);}

    public List<Cambio> cambios(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Cambio ultimoCambioPorMonedaId(Long id){
        return service.findLastByMonedaId(id);
    }


    public Cambio saveCambio(CambioInput input){
        ModelMapper m = new ModelMapper();
        Cambio e = m.map(input, Cambio.class);
        if(input.getMonedaId()!=null){
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(e);
    }

    public Boolean deleteCambio(Long id){
        return service.deleteById(id);
    }

    public Long countCambio(){
        return service.count();
    }


}
