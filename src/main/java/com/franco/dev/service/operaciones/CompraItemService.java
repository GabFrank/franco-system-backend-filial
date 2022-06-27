package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Compra;
import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.repository.operaciones.CompraItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class CompraItemService extends CrudService<CompraItem, CompraItemRepository> {
    private final CompraItemRepository repository;

    @Override
    public CompraItemRepository getRepository() {
        return repository;
    }

    public List<CompraItem> findByCompraId(Long id){
        return  repository.findByCompraId(id);
    }

    public List<CompraItem> findByProductoId(Long id) { return repository.findByProductoIdOrderByCreadoEnDesc(id); }

    public CompraItem findByPedidoItemId(Long id) {
        return repository.findByPedidoItemId(id);
    }

    public List<CompraItem> findByNotaRecepcionId(Long id){
        return repository.findByNotaRecepcionId(id);
    }

    @Override
    public CompraItem save(CompraItem entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        CompraItem e = super.save(entity);
        return e;
    }
}