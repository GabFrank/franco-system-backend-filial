package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.ClienteAdicional;
import com.franco.dev.graphql.personas.input.ClienteAdicionalInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.personas.ClienteAdicionalService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ClienteAdicionalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ClienteAdicionalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private PropagacionService propagacionService;
    
    public Optional<ClienteAdicional> clienteAdicional(Long id) {
        return service.findById(id);
    }

    public ClienteAdicional saveClienteAdicional(ClienteAdicionalInput input) {
        ModelMapper m = new ModelMapper();
        ClienteAdicional e = m.map(input, ClienteAdicional.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        e.setCliente(clienteService.findById(input.getClienteId()).orElse(null));
        e = service.save(e);
        propagacionService.propagarEntidad(e, TipoEntidad.CLIENTE_ADICIONAL);
        return e;
    }

    public Boolean deleteClienteAdicional(Long id) {
//        Boolean ok = service.deleteById(id);
//        if (ok) propagacionService.eliminarEntidad(id, TipoEntidad.CLIENTE_ADICIONAL);
        return false;
    }

    public Long countClienteAdicional() {
        return service.count();
    }

    public List<ClienteAdicional> clienteAdicionalPorPersonaId(Long id) {
        return service.findByPersonaId(id);
    }

    public List<ClienteAdicional> clienteAdicionalPorClienteId(Long id) {
        return service.findByClienteId(id);
    }

}
