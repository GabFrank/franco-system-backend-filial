package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.ProgramarPrecio;
import com.franco.dev.repository.operaciones.CobroRepository;
import com.franco.dev.repository.operaciones.ProgramarPrecioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class ProgramarPrecioService extends CrudService<ProgramarPrecio, ProgramarPrecioRepository> {
    private final ProgramarPrecioRepository repository;

    @Override
    public ProgramarPrecioRepository getRepository() {
        return repository;
    }

    @Override
    public ProgramarPrecio save(ProgramarPrecio entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        ProgramarPrecio e = super.save(entity);
        return e;
    }
}