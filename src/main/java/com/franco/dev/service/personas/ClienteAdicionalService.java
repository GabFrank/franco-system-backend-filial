package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.ClienteAdicional;
import com.franco.dev.repository.personas.ClienteAdicionalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ClienteAdicionalService extends CrudService<ClienteAdicional, ClienteAdicionalRepository> {

    private final ClienteAdicionalRepository repository;

    @Override
    public ClienteAdicionalRepository getRepository() {
        return repository;
    }

    public List<ClienteAdicional> findByPersonaId(Long id) {
        return repository.findByPersonaId(id);
    }

    public List<ClienteAdicional> findByClienteId(Long id) {
        return repository.findByClienteId(id);
    }

//    public List<ClienteAdicional> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByPersona(texto);
//    }
}
