package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.CobroRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class CobroService extends CrudService<Cobro, CobroRepository> {
    private final CobroRepository repository;

    @Autowired
    private Environment env;

    @Override
    public CobroRepository getRepository() {
        return repository;
    }

    @Override
    public Cobro save(Cobro entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        Cobro e = super.save(entity);
        return e;
    }

    @Override
    public Cobro saveAndSend(Cobro entity, Boolean recibir) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        Cobro e = super.save(entity);
        propagacionService.propagarEntidad(e, TipoEntidad.COBRO, recibir);
        return e;
    }
}