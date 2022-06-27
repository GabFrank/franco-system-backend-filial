package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Combo;
import com.franco.dev.repository.productos.ComboRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ComboService extends CrudService<Combo, ComboRepository> {

    @Autowired
    private final ComboRepository repository;

    @Override
    public ComboRepository getRepository() {
        return repository;
    }

    public List<Combo> findByProducto(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByProducto(texto);
    }

    public Combo findByProductoId(Long id){
        return  repository.findByProductoId(id);
    }


    @Override
    public Combo save(Combo entity) {
        Combo p = super.save(entity);
        return p;
    }
}
