package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.graphql.personas.input.ProveedorInput;
import com.franco.dev.graphql.personas.input.VendedorInput;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.personas.VendedorService;
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
public class VendedorGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VendedorService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private ProveedorService proveedorService;

    public Optional<Vendedor> vendedor(Long id) {return service.findById(id);}

    public List<Vendedor> vendedores(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Vendedor> vendedoresSearchByPersona(String texto){
        return service.findByPersonaNombre(texto);
    }

    public Vendedor saveVendedor(VendedorInput input){
        ModelMapper m = new ModelMapper();
        Vendedor e = m.map(input, Vendedor.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteVendedor(Long id){
        return service.deleteById(id);
    }

    public Long countVendedor(){
        return service.count();
    }

    public Vendedor vendedorPorPersona(Long id){
        return service.findByPersonaId(id);
    }

}
