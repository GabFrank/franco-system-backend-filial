package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Necesidad;
import com.franco.dev.repository.operaciones.NecesidadRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NecesidadService extends CrudService<Necesidad, NecesidadRepository> {
    private final NecesidadRepository repository;

    @Override
    public NecesidadRepository getRepository() {
        return repository;
    }

    public List<Necesidad> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto.toUpperCase());
    }

    public List<Necesidad> findByDate(String start, String end){
        return repository.findByDate(start, end);
    }

    @Override
    public Necesidad save(Necesidad entity) {
        Necesidad e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}