package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ClienteService extends CrudService<Cliente, ClienteRepository> {

    //

    private final ClienteRepository repository;
    private final CentralPersonasIntegrationService centralPersonasIntegrationService;

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

    @Override
    public Cliente save(Cliente entity) {
        Cliente synced = centralPersonasIntegrationService.syncCliente(entity);
        applySyncedValues(entity, synced);
        return super.save(entity);
    }

    private void applySyncedValues(Cliente target, Cliente source) {
        if (source == null) {
            return;
        }
        target.setId(source.getId());
        target.setTipo(source.getTipo());
        target.setCredito(source.getCredito());
        target.setCodigo(source.getCodigo());
        target.setTributa(source.getTributa());
        target.setVerificadoSet(source.getVerificadoSet());
        target.setCreadoEn(source.getCreadoEn());
    }
}


