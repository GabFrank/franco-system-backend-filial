package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SucursalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private Environment env;

    public Optional<Sucursal> sucursal(Long id) {
        return service.findById(id);
    }

    public List<Sucursal> sucursales(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Sucursal sucursalActual() {
        return service.findById(Long.valueOf(env.getProperty("sucursalId"))).orElse(null);
    }

    public List<Sucursal> sucursalesSearch(String texto) {
        return service.findByAll(texto);
    }

    public Long countSucursal() {
        return service.count();
    }

}
