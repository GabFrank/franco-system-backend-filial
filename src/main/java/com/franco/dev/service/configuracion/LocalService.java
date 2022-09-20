package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.repository.configuraciones.LocalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class LocalService extends CrudService<Local, LocalRepository> {

    private final LocalRepository repository;

    @Override
    public LocalRepository getRepository() {
        return repository;
    }

    public List<Local> findAll(){
        return repository.findAll();
    }

    @Override
    public Local save(Local entity) {
        if(entity.getId()==null){
            entity.setCreadoEn(LocalDateTime.now());
        }
        Local e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}