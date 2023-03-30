package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.repository.productos.CostosPorProductoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CostosPorProductoService extends CrudService<CostoPorProducto, CostosPorProductoRepository> {

    @Autowired
    private final CostosPorProductoRepository repository;
//    private final PersonaPublisher personaPublisher;


    @Override
    public CostosPorProductoRepository getRepository() {
        return repository;
    }

    public CostoPorProducto findLastByProductoId(Long prdoId){
        return repository.findLastByProductoId(prdoId);
    }

    public CostoPorProducto findByMovimientoStockId(Long id) {
        return repository.findByMovimientoStockId(id);
    }

    @Override
    public CostoPorProducto save(CostoPorProducto entity) {
        CostoPorProducto p = super.save(entity);
        return p;
    }
}
