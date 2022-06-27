package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.ComboItem;
import com.franco.dev.repository.productos.ComboItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ComboItemService extends CrudService<ComboItem, ComboItemRepository> {

    @Autowired
    private final ComboItemRepository repository;

    @Override
    public ComboItemRepository getRepository() {
        return repository;
    }

//    public List<ComboItem> findByProducto(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByCombo(texto);
//    }

    public List<ComboItem> findByProducto(Long id){
        return  repository.findByProductoId(id);
    }

    public List<ComboItem> findByCombo(Long id){
        return  repository.findByComboId(id);
    }


    @Override
    public ComboItem save(ComboItem entity) {
        ComboItem p = super.save(entity);
        return p;
    }
}
