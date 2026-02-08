package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.personas.PersonaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PersonaService extends CrudService<Persona, PersonaRepository> {

    private final PersonaRepository repository;
    private final CentralPersonasIntegrationService centralPersonasIntegrationService;
    // private final PersonaPublisher personaPublisher;

    @Override
    public PersonaRepository getRepository() {
        return repository;
    }

    public List<Persona> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findbyAll(texto);
    }

    public Persona findByDocumento(String documento) {
        return repository.findByDocumentoIgnoreCase(documento);
    }

    @Override
    public Persona save(Persona entity) {
        // IMPORTANTE: Las personas se guardan en el servidor central, no localmente.
        // El ID debe venir del servidor central. Si la sincronización falla, no debemos
        // guardar localmente.
        Persona synced = centralPersonasIntegrationService.syncPersona(entity);

        // Si la sincronización fue exitosa, aplicar los valores sincronizados
        if (synced != null) {
            applySyncedValues(entity, synced);
        } else {
            // Si synced es null, la sincronización falló silenciosamente
            // No debemos intentar guardar localmente sin ID
            throw new IllegalStateException(
                    "No se pudo sincronizar persona con el servidor central. La persona no tiene ID asignado.");
        }

        // Normalizar campos antes de guardar localmente (solo como caché/referencia)
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        if (entity.getNombre() != null)
            entity.setNombre(entity.getNombre().toUpperCase());
        if (entity.getApodo() != null)
            entity.setApodo(entity.getApodo().toUpperCase());
        if (entity.getDireccion() != null)
            entity.setDireccion(entity.getDireccion().toUpperCase());
        if (entity.getEmail() != null)
            entity.setEmail(entity.getEmail().toUpperCase());

        // Guardar localmente solo si tiene ID (viene del servidor central)
        // Esto es solo para tener una referencia local, el guardado real es en el
        // servidor central
        if (entity.getId() == null) {
            throw new IllegalStateException(
                    "No se puede guardar persona localmente sin ID. El ID debe venir del servidor central.");
        }

        Persona p = super.save(entity);
        // personaPublisher.publish(p);
        return p;
    }

    public List<Persona> saveAll(List<Persona> entityList) {
        return repository.saveAll(entityList);
    }

    /**
     * Guarda una persona solo localmente, sin intentar sincronizar con el servidor
     * central.
     * Este método es útil para operaciones que no requieren sincronización
     * inmediata,
     * como la actualización de imágenes.
     * 
     * IMPORTANTE: La persona debe tener un ID válido para poder guardarse
     * localmente.
     * 
     * @param entity La persona a guardar localmente
     * @return La persona guardada
     * @throws IllegalStateException si la persona no tiene ID
     */
    public Persona saveLocal(Persona entity) {
        if (entity.getId() == null) {
            throw new IllegalStateException(
                    "No se puede guardar persona localmente sin ID. La persona debe tener un ID válido.");
        }
        if (entity.getNombre() != null)
            entity.setNombre(entity.getNombre().toUpperCase());
        if (entity.getApodo() != null)
            entity.setApodo(entity.getApodo().toUpperCase());
        if (entity.getDireccion() != null)
            entity.setDireccion(entity.getDireccion().toUpperCase());
        if (entity.getEmail() != null)
            entity.setEmail(entity.getEmail().toUpperCase());

        Persona p = super.save(entity);
        return p;
    }

    private void applySyncedValues(Persona target, Persona source) {
        if (source == null) {
            return;
        }
        target.setId(source.getId());
        target.setNombre(source.getNombre());
        target.setApodo(source.getApodo());
        target.setDocumento(source.getDocumento());
        target.setEmail(source.getEmail());
        target.setDireccion(source.getDireccion());
        target.setTelefono(source.getTelefono());
        target.setSexo(source.getSexo());
        target.setSocialMedia(source.getSocialMedia());
        target.setImagenes(source.getImagenes());
        target.setNacimiento(source.getNacimiento());
        target.setCreadoEn(source.getCreadoEn());
    }
}