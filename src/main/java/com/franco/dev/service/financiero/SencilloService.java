package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Sencillo;
import com.franco.dev.repository.financiero.SencilloRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class SencilloService extends CrudService<Sencillo, SencilloRepository> {

    private final SencilloRepository repository;

    @Override
    public SencilloRepository getRepository() {
        return repository;
    }

//    public List<Sencillo> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

//    public List<Sencillo> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return repository.findByAll(texto);
//    }

    @Override
    public Sencillo save(Sencillo entity) {
        Sencillo e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}