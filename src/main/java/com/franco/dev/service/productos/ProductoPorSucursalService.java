package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.ProductoPorSucursal;
import com.franco.dev.repository.productos.PrecioPorSucursalRepository;
import com.franco.dev.repository.productos.ProductoPorSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ProductoPorSucursalService extends CrudService<ProductoPorSucursal, ProductoPorSucursalRepository> {

    @Autowired
    private final ProductoPorSucursalRepository repository;

    @Override
    public ProductoPorSucursalRepository getRepository() {
        return repository;
    }

//    public List<ProductoPorSucursal> findByProducto(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProductoId(texto);
//    }

    public List<ProductoPorSucursal> findByProductoId(Long id){
        return  repository.findByProductoId(id);
    }
    public List<ProductoPorSucursal> findBySucursalId(Long id){
        return  repository.findBySucursalId(id);
    }
    public ProductoPorSucursal findByProIdSucId(Long proId, Long sucId){
        return repository.findByProductoIdAndSucursalId(proId, sucId);
    }

//    public ProductoPorSucursal findByProductoPorSucursal(String texto){
//        texto = texto.replace(' ', '%');
//        return repository.findByProductoPorSucursal(texto.toUpperCase());
//    }

    @Override
    public ProductoPorSucursal save(ProductoPorSucursal entity) {
        ProductoPorSucursal p = super.save(entity);
        return p;
    }
}
