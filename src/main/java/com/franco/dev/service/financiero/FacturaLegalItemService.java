package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.FacturaLegalItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class FacturaLegalItemService extends CrudService<FacturaLegalItem, FacturaLegalItemRepository> {

    private final FacturaLegalItemRepository repository;

    @Override
    public FacturaLegalItemRepository getRepository() {
        return repository;
    }

    public List<FacturaLegalItem> findByFacturaLegalId(Long id) {
        return repository.findByFacturaLegalId(id);
    }

    @Override
    public FacturaLegalItem save(FacturaLegalItem entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        FacturaLegalItem e = super.save(entity);
        return e;
    }

    @Override
    public FacturaLegalItem saveAndSend(FacturaLegalItem entity, Boolean recibir) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        entity.setSucursalId(Long.valueOf(super.env.getProperty("sucursalId")));
        FacturaLegalItem e = super.save(entity);
        super.propagacionService.propagarEntidad(e, TipoEntidad.FACTURA_ITEM, false);
        return e;
    }
}