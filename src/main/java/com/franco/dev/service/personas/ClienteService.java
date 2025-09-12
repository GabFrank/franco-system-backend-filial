package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.sifen.service.SifenService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ClienteService extends CrudService<Cliente, ClienteRepository> {

    //

    private final ClienteRepository repository;

    @Autowired
    private final SifenService sifenService;

    @Override
    public ClienteRepository getRepository() {
        return repository;
    }

    public Cliente findByPersonaId(Long id){
        return repository.findByPersonaId(id);
    }

    public Cliente findByPersonaDocumento(String texto){
        return repository.findByPersonaDocumento(texto);
    }

    public List<Cliente> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByPersona(texto.toUpperCase());
    }

    public com.franco.dev.service.sifen.dto.response.ConsultaRucResponse consultaRuc(String ruc) {
        return sifenService.consultaRuc(ruc);
    }

}


