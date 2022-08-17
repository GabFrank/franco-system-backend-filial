package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class FacturaLegalService extends CrudService<FacturaLegal, FacturaLegalRepository> {

    private final FacturaLegalRepository repository;

    @Override
    public FacturaLegalRepository getRepository() {
        return repository;
    }

    public List<FacturaLegal> findByCajaId(Long id){
        return repository.findByCajaId(id);
    }

    @Override
    public FacturaLegal save(FacturaLegal entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        FacturaLegal e = super.save(entity);
        return e;
    }
}