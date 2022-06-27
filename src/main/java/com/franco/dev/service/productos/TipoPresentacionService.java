package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.domain.productos.TipoPresentacion;
import com.franco.dev.repository.productos.TipoPrecioRepository;
import com.franco.dev.repository.productos.TipoPresentacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class TipoPresentacionService extends CrudService<TipoPresentacion, TipoPresentacionRepository> {

    private final TipoPresentacionRepository repository;

    @Override
    public TipoPresentacionRepository getRepository() {
        return repository;
    }

    public List<TipoPresentacion> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto.toUpperCase());
    }

    @Override
    public TipoPresentacion save(TipoPresentacion entity) {
        TipoPresentacion e = super.save(entity);
        e.setDescripcion(e.getDescripcion().toUpperCase());
//        personaPublisher.publish(p);
        return e;
    }
}
