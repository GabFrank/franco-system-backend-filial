package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.repository.productos.FamiliaRepository;
import com.franco.dev.repository.productos.TipoPrecioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class TipoPrecioService extends CrudService<TipoPrecio, TipoPrecioRepository> {

    private final TipoPrecioRepository repository;

    @Override
    public TipoPrecioRepository getRepository() {
        return repository;
    }

    public List<TipoPrecio> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto.toUpperCase());
    }

    @Override
    public TipoPrecio save(TipoPrecio entity) {
        TipoPrecio e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}
