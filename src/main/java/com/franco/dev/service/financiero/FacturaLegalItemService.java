package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.repository.financiero.FacturaLegalItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class FacturaLegalItemService extends CrudService<FacturaLegalItem, FacturaLegalItemRepository> {

    private final FacturaLegalItemRepository repository;

    @Override
    public FacturaLegalItemRepository getRepository() {
        return repository;
    }

    @Override
    public FacturaLegalItem save(FacturaLegalItem entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        FacturaLegalItem e = super.save(entity);
        return e;
    }
}