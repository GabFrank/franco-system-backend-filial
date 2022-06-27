package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.domain.personas.VendedorProveedor;
import com.franco.dev.graphql.personas.input.VendedorInput;
import com.franco.dev.graphql.personas.input.VendedorProveedorInput;
import com.franco.dev.service.personas.*;
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
public class VendedorProveedorGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VendedorProveedorService service;

    @Autowired
    private VendedorService vendedorService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProveedorService proveedorService;

    public Optional<VendedorProveedor> vendedorProveedor(Long id) {return service.findById(id);}

    public List<VendedorProveedor> vendedorProveedores(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Vendedor> vendedoresPorProveedor(Long id){
        return service.findByProveedorId(id);
    }

    public List<Proveedor> proveedoresPorVendedor(Long id){
        return service.findByVendedorId(id);
    }

    public VendedorProveedor saveVendedorProveedor(VendedorProveedorInput input){
        ModelMapper m = new ModelMapper();
        VendedorProveedor e = m.map(input, VendedorProveedor.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        e.setVendedor(vendedorService.findById(input.getVendedorId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteVendedorProveedor(Long id){
        return service.deleteById(id);
    }

    public Long countVendedorProveedor(){
        return service.count();
    }

}
