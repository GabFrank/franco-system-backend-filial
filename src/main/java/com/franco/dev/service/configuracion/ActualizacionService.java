package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.Actualizacion;
import com.franco.dev.domain.configuracion.enums.TipoActualizacion;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.configuraciones.ActualizacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class ActualizacionService extends CrudService<Actualizacion, ActualizacionRepository> {

    private final ActualizacionRepository repository;

    @Override
    public ActualizacionRepository getRepository() {
        return repository;
    }

    public Actualizacion findLast(TipoActualizacion tipo){
        return repository.findFirstByTipoOrderByIdDesc(tipo);
    }

    public Actualizacion findLast(){
        return repository.findFirstByOrderByIdDesc();
    }

    @Override
    public Actualizacion save(Actualizacion entity) {
        Actualizacion e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public Actualizacion saveAndSend(Actualizacion entity, Boolean recibir) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        Actualizacion e = super.save(entity);
        propagacionService.propagarEntidad(e, TipoEntidad.ACTUALIZACION, recibir);
        return e;
    }
}