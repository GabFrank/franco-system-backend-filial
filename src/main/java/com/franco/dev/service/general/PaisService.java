package com.franco.dev.service.general;

import com.franco.dev.domain.general.Pais;
import com.franco.dev.repository.general.PaisRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PaisService extends CrudService<Pais, PaisRepository> {

    private final PaisRepository repository;

    @Override
    public PaisRepository getRepository() {
        return repository;
    }

    public List<Pais> findByDescripcion(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByDescripcion(texto);
    }

    public List<Pais> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findbyAll(texto);
    }

    @Override
    public Pais save(Pais entity) {
        Pais e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}