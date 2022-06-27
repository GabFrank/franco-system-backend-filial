package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.repository.productos.CostosPorSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CostosPorSucursalService extends CrudService<CostoPorProducto, CostosPorSucursalRepository> {

    @Autowired
    private final CostosPorSucursalRepository repository;
//    private final PersonaPublisher personaPublisher;


    @Override
    public CostosPorSucursalRepository getRepository() {
        return repository;
    }

    public CostoPorProducto findLastByProductoId(Long prdoId){
        return repository.findLastByProductoId(prdoId);
    }

    public CostoPorProducto findByMovimientoStockId(Long id) {
        return repository.findByMovimientoStockId(id);
    }


}
