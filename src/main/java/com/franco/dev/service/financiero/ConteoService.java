package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.ConteoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ConteoService extends CrudService<Conteo, ConteoRepository> {

    private final ConteoRepository repository;

    @Override
    public ConteoRepository getRepository() {
        return repository;
    }

//    public List<Conteo> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

//    public List<Conteo> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return repository.findByAll(texto);
//    }

    public Double getTotalPorMoneda(Long conteoId, Long monedaId){
        return repository.getTotalPorMoneda(conteoId, monedaId);
    }

    @Override
    public Conteo save(Conteo entity) {
        Conteo e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}