package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.service.financiero.TimbradoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TimbradoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TimbradoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<Timbrado> timbrado(Long id) {
        return service.findById(id);
    }

    public List<Timbrado> timbrados(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countTimbrado() {
        return service.count();
    }


}
