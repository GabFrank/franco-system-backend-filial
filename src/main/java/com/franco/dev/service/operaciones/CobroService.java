package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.repository.operaciones.CobroRepository;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class CobroService extends CrudService<Cobro, CobroRepository> {
    private final CobroRepository repository;

    @Override
    public CobroRepository getRepository() {
        return repository;
    }

    @Override
    public Cobro save(Cobro entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getCreadoEn()==null) entity.setCreadoEn(LocalDateTime.now());
        Cobro e = super.save(entity);
        return e;
    }
}