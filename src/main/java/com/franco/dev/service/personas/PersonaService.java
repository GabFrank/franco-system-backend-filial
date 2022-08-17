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
//    private final PersonaPublisher personaPublisher;


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
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        if (entity.getNombre() != null) entity.setNombre(entity.getNombre().toUpperCase());
        if (entity.getApodo() != null) entity.setApodo(entity.getApodo().toUpperCase());
        if (entity.getDireccion() != null) entity.setDireccion(entity.getDireccion().toUpperCase());
        if (entity.getEmail() != null) entity.setEmail(entity.getEmail().toUpperCase());
        Persona p = super.save(entity);
//        personaPublisher.publish(p);
        return p;
    }

    public List<Persona> saveAll(List<Persona> entityList) {
        return repository.saveAll(entityList);
    }
}
