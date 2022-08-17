package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.financiero.TimbradoService;
import com.franco.dev.service.general.PaisService;
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
public class TimbradoDetalleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TimbradoDetalleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private TimbradoService timbradoService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<TimbradoDetalle> timbradoDetalle(Long id) {
        return service.findById(id);
    }

    public List<TimbradoDetalle> timbradoDetalles(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<TimbradoDetalle> timbradoDetallePorTimbradoId(Long id) {
        return service.findByTimbradoId(id);
    }

    public Long countTimbradoDetalle() {
        return service.count();
    }


}
