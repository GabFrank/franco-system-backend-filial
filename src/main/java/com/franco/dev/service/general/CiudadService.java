package com.franco.dev.service.general;

import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.repository.general.CiudadRepository;
import com.franco.dev.repository.general.PaisRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CiudadService extends CrudService<Ciudad, CiudadRepository> {

    private final CiudadRepository repository;

    @Override
    public CiudadRepository getRepository() {
        return repository;
    }

    public List<Ciudad> findByDescripcion(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByDescripcion(texto);
    }

    public List<Ciudad> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto.toUpperCase());
    }

    @Override
    public Ciudad save(Ciudad entity) {
        Ciudad e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}