package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.SesionInventario;
import com.franco.dev.repository.operaciones.SesionInventarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class SesionInventarioService extends CrudService<SesionInventario, SesionInventarioRepository> {

    private final Logger log = LoggerFactory.getLogger(SesionInventarioService.class);

    private final SesionInventarioRepository repository;

    @Override
    public SesionInventarioRepository getRepository() {
        return repository;
    }

    @Override
    public SesionInventario save(SesionInventario entity) {
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        SesionInventario e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}