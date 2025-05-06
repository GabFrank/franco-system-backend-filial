package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.repository.administrativo.MarcacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MarcacionService extends CrudService<Marcacion, MarcacionRepository> {

    private final MarcacionRepository repository;

    @Override
    public MarcacionRepository getRepository() {
        return repository;
    }

    @Override
    public Marcacion save(Marcacion entity) {
        Marcacion e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}