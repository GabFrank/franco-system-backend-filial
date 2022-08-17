package com.franco.dev.graphql.empresarial.resolver;

import com.franco.dev.domain.empresarial.PuntoDeVenta;
import com.franco.dev.service.empresarial.PuntoDeVentaService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PuntoDeVentaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PuntoDeVentaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<PuntoDeVenta> puntoDeVenta(Long id) {
        return service.findById(id);
    }

    public List<PuntoDeVenta> puntoDeVentas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<PuntoDeVenta> puntoDeVentaPorSucursalId(Long id) {
        return service.findBySucursalId(id);
    }

    public Long countPuntoDeVenta() {
        return service.count();
    }


}
