package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
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
public class FacturaLegalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FacturaLegalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private VentaService ventaService;

    public Optional<FacturaLegal> facturaLegal(Long id) {
        return service.findById(id);
    }

    public List<FacturaLegal> facturaLegales(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }


    public FacturaLegal saveFacturaLegal(FacturaLegalInput input) {
        ModelMapper m = new ModelMapper();
        FacturaLegal e = m.map(input, FacturaLegal.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getClienteId() != null) e.setCliente(clienteService.findById(input.getClienteId()).orElse(null));
        if (input.getVentaId() != null) e.setVenta(ventaService.findById(input.getVentaId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteFacturaLegal(Long id) {
        return service.deleteById(id);
    }

    public Long countFacturaLegal() {
        return service.count();
    }


}
