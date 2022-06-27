package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CuentaBancariaService extends CrudService<CuentaBancaria, CuentaBancariaRepository> {

    private final CuentaBancariaRepository repository;

    @Override
    public CuentaBancariaRepository getRepository() {
        return repository;
    }

//    public List<CuentaBancaria> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<CuentaBancaria> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto);
    }

    @Override
    public CuentaBancaria save(CuentaBancaria entity) {
        CuentaBancaria e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}