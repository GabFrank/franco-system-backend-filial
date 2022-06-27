package com.franco.dev.service.general;

import com.franco.dev.domain.general.Barrio;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.repository.general.BarrioRepository;
import com.franco.dev.repository.general.CiudadRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class BarrioService extends CrudService<Barrio, BarrioRepository> {

    private final BarrioRepository repository;

    @Override
    public BarrioRepository getRepository() {
        return repository;
    }

    public List<Barrio> findByDescripcion(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByDescripcion(texto);
    }

    public List<Barrio> findByCiudadId(Long id){
        return repository.findByCiudadId(id);
    }

    public List<Barrio> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto.toUpperCase());
    }

    @Override
    public Barrio save(Barrio entity) {
        Barrio e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}