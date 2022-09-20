package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.repository.operaciones.InventarioProductoItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class InventarioProductoItemService extends CrudService<InventarioProductoItem, InventarioProductoItemRepository> {
    private final InventarioProductoItemRepository repository;

    @Override
    public InventarioProductoItemRepository getRepository() {
        return repository;
    }

    public List<InventarioProductoItem> findByInventarioProductoId(Long id) {
        return repository.findByInventarioProductoId(id);
    }

    public List<InventarioProductoItem> findByProductoId(Long id) {
        return repository.findByProductoId(id);
    }

    @Override
    public InventarioProductoItem save(InventarioProductoItem entity) {
        InventarioProductoItem e = new InventarioProductoItem();
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        e = super.save(entity);
        return e;
    }
}