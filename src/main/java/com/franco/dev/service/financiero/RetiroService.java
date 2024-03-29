package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.franco.dev.rabbit.enums.TipoEntidad.RETIRO;

@Service
@AllArgsConstructor
public class RetiroService extends CrudService<Retiro, RetiroRepository> {

    private final RetiroRepository repository;

    @Override
    public RetiroRepository getRepository() {
        return repository;
    }

//    public List<Retiro> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

//    public List<Retiro> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return repository.findByAll(texto);
//    }

    public List<Retiro> findByCajaSalidaId(Long id){
        return repository.findByCajaSalidaId(id);
    }

    @Override
    public Retiro save(Retiro entity) {
        Retiro e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public Retiro saveAndSend(Retiro entity, Boolean recibir) {
        entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        Retiro e = super.save(entity);
//        personaPublisher.publish(p);
        propagacionService.propagarEntidad(e, RETIRO, recibir);
        return e;
    }
}