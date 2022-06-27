package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ClienteService extends CrudService<Cliente, ClienteRepository> {

    private final ClienteRepository repository;


    @Override
    public ClienteRepository getRepository() {
        return repository;
    }

    public Cliente findByPersonaId(Long id){
        return repository.findByPersonaId(id);
    }

    public List<Cliente> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByPersona(texto);
    }
}
