package com.franco.dev.graphql.personas;

import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.sifen.dto.response.ConsultaRucResponse;

import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
    private SucursalService sucursalService;

    @Autowired
    private ContactoService contactoService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<Cliente> cliente(Long id) {
        return service.findById(id);
    }

    public List<Cliente> clientePorTelefono(String texto) {
        List<Contacto> contactoList = contactoService.findByTelefonoOrNombre(texto);
        List<Cliente> clienteList = new ArrayList<>();
        for (Contacto c : contactoList) {
            clienteList.add(service.findByPersonaId(c.getPersona().getId()));
        }
        return clienteList;
    }

    public List<Cliente> clientes(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Cliente> clientePorPersona(String texto) {
        return service.findByAll(texto);
    }

    public Cliente clientePorPersonaDocumento(String texto) {
        Cliente e = service.findByPersonaDocumento(texto);
        return e;
    }

    public Cliente saveCliente(ClienteInput input) {
        Boolean modifPersona = false;
        ModelMapper m = new ModelMapper();
        Cliente e = m.map(input, Cliente.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        if (input.getPersonaId() != null) e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        if (e.getPersona() == null) {
            Persona newPersona = new Persona();
            newPersona.setNombre(input.getNombre());
            newPersona.setDireccion(input.getDireccion());
            newPersona.setDocumento(input.getDocumento());
            if (e.getUsuario() != null) newPersona.setUsuario(e.getUsuario());
            newPersona.setCreadoEn(LocalDateTime.now());
            newPersona = personaService.save(newPersona);
            e.setPersona(newPersona);
        }

        if (input.getDireccion() != null && !input.getDireccion().equals(e.getPersona().getDireccion())) {
            e.getPersona().setDireccion(input.getDireccion());
            modifPersona = true;
        }
        if (input.getNombre() != null && !input.getNombre().equals(e.getPersona().getNombre())) {
            e.getPersona().setNombre(input.getNombre());
            modifPersona = true;
        }
        if (modifPersona) {
            personaService.save(e.getPersona());
        }
        return service.save(e);
    }

    public Boolean deleteCliente(Long id) {
        return service.deleteById(id);
    }

    public Long countCliente() {
        return service.count();
    }

    public Cliente clientePorPersonaId(Long id) {
        return service.findByPersonaId(id);
    }

    public Cliente clientePorPersonaIdFromServer(Long id) {
//        return propagacionService.solicitarCliente(id);
        return null;
    }

    public ConsultaRucResponse consultaRuc(String ruc) {
        ConsultaRucResponse sifenResponse = service.consultaRuc(ruc);
        ModelMapper m = new ModelMapper();
        return m.map(sifenResponse, ConsultaRucResponse.class);
    }

}
