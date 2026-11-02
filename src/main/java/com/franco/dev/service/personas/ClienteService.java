package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
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

    /** Solo altas, como {@link PersonaService#save}. */
    @Override
    public Cliente save(Cliente entity) {
        if (entity.getId() != null) {
            throw new IllegalStateException(PersonaService.EDICION_EN_CENTRAL);
        }
        Cliente synced = centralPersonasIntegrationService.syncCliente(entity);
        if (synced == null || synced.getId() == null) {
            throw new IllegalStateException("No se pudo sincronizar cliente con el servidor central. El cliente no tiene ID asignado.");
        }
        if (centralPersonasIntegrationService.llegaPorReplica("personas.cliente")) {
            Long id = synced.getId();
            return centralPersonasIntegrationService.esperarReplica(() -> repository.findById(id))
                    .orElseThrow(() -> {
                        log.warn("El cliente {} se creo en el central pero no llego por la replica a tiempo", id);
                        return new IllegalStateException("El cliente " + id + " se creo en el servidor central pero todavia no llego a esta sucursal.");
                    });
        }
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


