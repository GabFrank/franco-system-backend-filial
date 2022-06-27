package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.service.personas.*;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class VendedorResolver implements GraphQLResolver<Vendedor> {

    @Autowired
    private PersonaService personaService;

    @Autowired
    private VendedorService usuarioService;

    @Autowired
    private VendedorProveedorService vendedorProveedorService;

    @Autowired
    private ProveedorService proveedorService;

    public String nombrePersona(Vendedor e){
        return e.getPersona().getNombre();
    }

    public List<Proveedor> proveedores(Vendedor e){
        return proveedorService.findByVendedorId(e.getId());
    }

}
