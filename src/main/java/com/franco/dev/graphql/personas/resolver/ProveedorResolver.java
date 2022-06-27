package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.general.enums.DiasSemana;
import com.franco.dev.domain.personas.*;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.personas.*;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ProveedorResolver implements GraphQLResolver<Proveedor> {

    @Autowired
    private VendedorProveedorService vendedorProveedorService;

    @Autowired
    private ProveedorDiasVisitaService proveedorDiasVisitaService;

    @Autowired
    private ProductoService productoService;

    public List<Vendedor> vendedores(Proveedor e){
        return vendedorProveedorService.findByProveedorId(e.getId());
    }

    public List<DiasSemana> diasVisita(Proveedor e){
        return proveedorDiasVisitaService.findByProveedorId(e.getId());
    }

    public List<Producto> productos(Proveedor e) { return productoService.findByProveedorId(e.getId(), ""); }
}
