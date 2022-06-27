package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.graphql.personas.input.ClienteUpdateInput;
import com.franco.dev.graphql.personas.input.ProveedorInput;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
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
public class ProveedorGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProveedorService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    public Optional<Proveedor> proveedor(Long id) {return service.findById(id);}

    public List<Proveedor> proveedores(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Proveedor> proveedorPorVendedor(Long id){
        return service.findByVendedorId(id);
    }

    public Proveedor saveProveedor(ProveedorInput input){
        ModelMapper m = new ModelMapper();
        Proveedor e = m.map(input, Proveedor.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteProveedor(Long id){
        return service.deleteById(id);
    }

    public Long countProveedor(){
        return service.count();
    }

    public Proveedor proveedorPorPersona(Long id){
        return service.findByPersonaId(id);
    }

    public List<Proveedor> proveedorSearchByPersona(String texto) { return service.findByPersonaNombre(texto); }

}
