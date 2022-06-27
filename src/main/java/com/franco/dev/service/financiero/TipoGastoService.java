package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.TipoGastoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class TipoGastoService extends CrudService<TipoGasto, TipoGastoRepository> {

    private final TipoGastoRepository repository;

    @Override
    public TipoGastoRepository getRepository() {
        return repository;
    }

//    public List<TipoGasto> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<TipoGasto> findByAll(String texto){
        texto = texto.replace(' ', '%').toUpperCase();
        return repository.findByAll(texto);
    }
    public List<TipoGasto> findByClasificacionGastoId(Long id){
        return repository.findByClasificacionGastoId(id);
    }

    public List<TipoGasto> findRoot(){
       return repository.findRoot();
    }

    @Override
    public TipoGasto save(TipoGasto entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        if(entity.getCreadoEn()==null) entity.setCreadoEn(LocalDateTime.now());
        entity.setDescripcion(entity.getDescripcion().toUpperCase());
        TipoGasto e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public List<TipoGasto> saveAll(List<TipoGasto> entityList) {
        List<TipoGasto> list = findAll2();
        Boolean isNew = list.size()<1;
        list = new ArrayList<>();
        for(TipoGasto e: entityList){
            if(isNew) e.setClasificacionGasto(null);
            list.add(save(e));
        }
        return list;
    }
}