package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.CambioCaja;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.CambioCajaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CambioCajaService extends CrudService<CambioCaja, CambioCajaRepository> {

    private final CambioCajaRepository repository;

    @Override
    public CambioCajaRepository getRepository() {
        return repository;
    }

//    public List<CambioCaja> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

//    public List<CambioCaja> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return repository.findByAll(texto);
//    }

    @Override
    public CambioCaja save(CambioCaja entity) {
        CambioCaja e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}