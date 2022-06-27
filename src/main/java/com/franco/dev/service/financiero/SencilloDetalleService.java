package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.SencilloDetalle;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.SencilloDetalleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SencilloDetalleService extends CrudService<SencilloDetalle, SencilloDetalleRepository> {

    private final SencilloDetalleRepository repository;

    @Override
    public SencilloDetalleRepository getRepository() {
        return repository;
    }

//    public List<SencilloDetalle> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<SencilloDetalle> findBySencilloId(Long id){
        return repository.findBySencilloId(id);
    }

    @Override
    public SencilloDetalle save(SencilloDetalle entity) {
        SencilloDetalle e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}