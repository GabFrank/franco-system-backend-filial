package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class BancoService extends CrudService<Banco, BancoRepository> {

    private final BancoRepository repository;

    @Override
    public BancoRepository getRepository() {
        return repository;
    }

//    public List<Banco> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<Banco> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto);
    }

    @Override
    public Banco save(Banco entity) {
        Banco e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}