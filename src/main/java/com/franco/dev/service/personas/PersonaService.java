package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.repository.personas.PersonaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
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

    public Page<Persona> findByAll(String texto, Pageable pageable) {
        texto = texto.replace(' ', '%');
        return repository.findbyAll(texto, pageable);
    }

    public Persona findByDocumento(String documento) {
        return repository.findByDocumentoIgnoreCase(documento);
    }

    static final String EDICION_EN_CENTRAL = "Las ediciones de personas y clientes se hacen en el servidor central.";

    /**
     * Solo altas: la persona se crea en el central. Si la tabla llega por replica se espera la fila (escribirla
     * aca trabaria la suscripcion); si no, se guarda local con el id del central.
     */
    @Override
    public Persona save(Persona entity) {
        if (entity.getId() != null) {
            throw new IllegalStateException(EDICION_EN_CENTRAL);
        }
        Persona synced = centralPersonasIntegrationService.syncPersona(entity);
        if (synced == null || synced.getId() == null) {
            throw new IllegalStateException(
                    "No se pudo sincronizar persona con el servidor central. La persona no tiene ID asignado.");
        }
        if (centralPersonasIntegrationService.llegaPorReplica("personas.persona")) {
            Long id = synced.getId();
            return centralPersonasIntegrationService.esperarReplica(() -> repository.findById(id))
                    .orElseThrow(() -> {
                        log.warn("La persona {} se creo en el central pero no llego por la replica a tiempo", id);
                        return new IllegalStateException("La persona " + id + " se creo en el servidor central pero todavia no llego a esta sucursal.");
                    });
        }
        applySyncedValues(entity, synced);
        if (entity.getCreadoEn() == null) {
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
        return super.save(entity);
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