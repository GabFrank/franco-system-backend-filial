package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MonedaBilletes;
import com.franco.dev.repository.financiero.MonedaBilleteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class MonedaBilleteService extends CrudService<MonedaBilletes, MonedaBilleteRepository> {

    private final MonedaBilleteRepository repository;

    @Override
    public MonedaBilleteRepository getRepository() {
        return repository;
    }

//    public List<MonedaBillete> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<MonedaBilletes> findByMonedaId(Long id){
        return repository.findByMonedaId(id);
    }

    @Override
    public MonedaBilletes save(MonedaBilletes entity) {
        MonedaBilletes e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}