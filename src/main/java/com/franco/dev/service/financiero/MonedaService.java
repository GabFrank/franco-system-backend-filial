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

    /**
     * La moneda de la empresa: la marcada como principal; si no hay, la del guarani por nombre; si
     * tampoco, la primera. Mismo criterio que MonedaService.empresa() del central.
     */
    public Moneda principal() {
        List<Moneda> todas = findAll2();
        if (todas == null || todas.isEmpty()) return null;
        return todas.stream().filter(m -> Boolean.TRUE.equals(m.getPrincipal())).findFirst()
                .orElseGet(() -> todas.stream()
                        .filter(m -> m.getDenominacion() != null && m.getDenominacion().toUpperCase().contains("GUARAN"))
                        .findFirst().orElse(todas.get(0)));
    }
}
