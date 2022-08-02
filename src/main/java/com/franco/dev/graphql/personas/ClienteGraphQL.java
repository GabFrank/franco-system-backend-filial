package com.franco.dev.graphql.personas;

import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.graphql.personas.input.ClienteUpdateInput;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ClienteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ClienteService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private ContactoService contactoService;

    public Optional<Cliente> cliente(Long id) {return service.findById(id);}

    public List<Cliente> clientePorTelefono(String texto){
        List<Contacto> contactoList = contactoService.findByTelefonoOrNombre(texto);
        List<Cliente> clienteList = new ArrayList<>();
        for(Contacto c : contactoList){
            clienteList.add(service.findByPersonaId(c.getPersona().getId()));
        }
        return clienteList;
    }

    public List<Cliente> clientes(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Cliente> clientePorPersona(String texto){
        return service.findByAll(texto);
    }

    public Cliente clientePorPersonaDocumento(String texto){
        Cliente e = service.findByPersonaDocumento(texto);
        return e;
    }

    public Cliente saveCliente(ClienteInput input){
        ModelMapper m = new ModelMapper();
        Cliente e = m.map(input, Cliente.class);
        e.setUsuarioId(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteCliente(Long id){
        return service.deleteById(id);
    }

    public Long countCliente(){
        return service.count();
    }

    public Cliente clientePorPersonaId(Long id){
        return service.findByPersonaId(id);
    }



}
