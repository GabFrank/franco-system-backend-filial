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
        // IMPORTANTE: Los clientes se guardan en el servidor central, no localmente.
        // El ID debe venir del servidor central. Si la sincronización falla, no debemos guardar localmente.
        Cliente synced = centralPersonasIntegrationService.syncCliente(entity);
        
        // Si la sincronización fue exitosa, aplicar los valores sincronizados
        if (synced != null) {
            applySyncedValues(entity, synced);
        } else {
            // Si synced es null, la sincronización falló silenciosamente
            // No debemos intentar guardar localmente sin ID
            throw new IllegalStateException("No se pudo sincronizar cliente con el servidor central. El cliente no tiene ID asignado.");
        }
        
        // Guardar localmente solo si tiene ID (viene del servidor central)
        // Esto es solo para tener una referencia local, el guardado real es en el servidor central
        if (entity.getId() == null) {
            throw new IllegalStateException("No se puede guardar cliente localmente sin ID. El ID debe venir del servidor central.");
        }
        
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


