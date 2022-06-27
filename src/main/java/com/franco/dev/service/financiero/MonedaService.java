package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class MonedaService extends CrudService<Moneda, MonedaRepository> {

    private final MonedaRepository repository;

    @Override
    public MonedaRepository getRepository() {
        return repository;
    }

//    public List<Moneda> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<Moneda> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto);
    }

    public Moneda findByDescripcion(String descripcion){
        return repository.findByDenominacion(descripcion);
    }

    @Override
    public Moneda save(Moneda entity) {
        Moneda e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public List<Moneda> findAll2() {
        return repository.findAllByOrderByIdAsc();
    }
}