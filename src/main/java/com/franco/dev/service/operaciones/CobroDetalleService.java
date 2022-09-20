package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.CobroDetalleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class CobroDetalleService extends CrudService<CobroDetalle, CobroDetalleRepository> {
    private final CobroDetalleRepository repository;
    @Autowired
    MovimientoStockService movimientoStockService;
    @Autowired
    private Environment env;

    @Override
    public CobroDetalleRepository getRepository() {
        return repository;
    }

    //    public List<CobroDetalle> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//
    public List<CobroDetalle> findByCobroId(Long id) {
        return repository.findByCobroId(id);
    }

    public List<CobroDetalle> findByCajaId(Long id) {
        return repository.findByCajaId(id);
    }

    @Override
    public CobroDetalle save(CobroDetalle entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        CobroDetalle e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public CobroDetalle saveAndSend(CobroDetalle entity, Boolean recibir) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        CobroDetalle e = super.save(entity);
//        personaPublisher.publish(p);
        propagacionService.propagarEntidad(e, TipoEntidad.COBRO_DETALLE, recibir);
        return e;
    }
}