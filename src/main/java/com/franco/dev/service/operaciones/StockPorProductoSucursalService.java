package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.StockPorProductoSucursal;
import com.franco.dev.repository.operaciones.StockPorProductoSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class StockPorProductoSucursalService extends CrudService<StockPorProductoSucursal, StockPorProductoSucursalRepository> {
    private final StockPorProductoSucursalRepository repository;

    @Override
    public StockPorProductoSucursalRepository getRepository() {
        return repository;
    }


}