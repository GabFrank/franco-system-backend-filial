package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Ingrediente;
import com.franco.dev.domain.productos.ProductoImagen;
import com.franco.dev.repository.productos.IngredienteRepository;
import com.franco.dev.repository.productos.ProductoImagenRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ProductoImagenService extends CrudService<ProductoImagen, ProductoImagenRepository> {

    @Autowired
    private final ProductoImagenRepository repository;

    @Override
    public ProductoImagenRepository getRepository() {
        return repository;
    }

    public List<ProductoImagen> findByProductoId(Long id){
        return  repository.findByProductoId(id);
    }

    public ProductoImagen findImagenPrincipalByProductoId(Long id){
        return  repository.findImagenPrincipalByProductoId(id);
    }

    @Override
    public ProductoImagen save(ProductoImagen entity) {
        ProductoImagen p = super.save(entity);
//        personaPublisher.publish(p);
        return p;
    }
}
