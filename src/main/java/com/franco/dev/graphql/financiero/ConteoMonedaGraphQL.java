package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.security.Unsecured;
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

    public Optional<ConteoMoneda> conteoMoneda(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<ConteoMoneda> conteoMonedas(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Unsecured
    public ConteoMoneda saveConteoMoneda(ConteoMonedaInput input) {
        ModelMapper m = new ModelMapper();
        ConteoMoneda e = m.map(input, ConteoMoneda.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.saveAndSend(e, false);
    }

    public List<ConteoMoneda> conteoMonedasPorConteoId(Long id, Long sucId) {
        return service.findByConteoId(id);
    }

//    public List<ConteoMoneda> conteoMonedasSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteConteoMoneda(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countConteoMoneda() {
        return service.count();
    }


}
