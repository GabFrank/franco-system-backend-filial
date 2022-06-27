package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.ComboItem;
import com.franco.dev.domain.productos.ProductoIngrediente;
import com.franco.dev.repository.productos.ComboItemRepository;
import com.franco.dev.repository.productos.ProductoIngredienteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ProductoIngredienteService extends CrudService<ProductoIngrediente, ProductoIngredienteRepository> {

    @Autowired
    private final ProductoIngredienteRepository repository;

    @Override
    public ProductoIngredienteRepository getRepository() {
        return repository;
    }

    public List<ProductoIngrediente> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto);
    }

    public List<ProductoIngrediente> findByProducto(Long id){
        return  repository.findByProductoId(id);
    }

    public List<ProductoIngrediente> findByIngrediente(Long id){
        return  repository.findByIngredienteId(id);
    }


    @Override
    public ProductoIngrediente save(ProductoIngrediente entity) {
        ProductoIngrediente p = super.save(entity);
        return p;
    }
}
