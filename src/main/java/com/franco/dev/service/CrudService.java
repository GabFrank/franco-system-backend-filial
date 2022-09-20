package com.franco.dev.service;

import com.franco.dev.repository.HelperRepository;
import com.franco.dev.service.rabbitmq.PropagacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Service
public abstract class CrudService<T, Repository extends HelperRepository<T, Long>> implements ICrudService<Repository> {

    public static final String GUARDAR = "0";
    public static final String ELIMINAR = "1";
    public static final String ACTUALIZAR = "2";
    public static final Logger log = Logger.getLogger(String.valueOf(CrudService.class));
    @Autowired
    public Environment env;

    public PropagacionService propagacionService;

    @Autowired
    public void setpropagacionService(PropagacionService propagacionService) {
        this.propagacionService = propagacionService;
    }

    public List<T> findAll(Pageable pageable) {
        return (List<T>) getRepository().findAll(pageable).getContent();
    }

    public List<T> findAll2() {
        return (List<T>) getRepository().findAllByOrderByIdAsc();
    }

    public Optional<T> findById(Long id) {
        if (id == null) {
            return null;
        }
        return getRepository().findById(id);
    }

    public T getOne(Long id) {
        return getRepository().getOne(id);
    }

    @Transactional
    public T save(T entity) {
        return getRepository().save(entity);
    }


    @Transactional
    public T saveAndSend(T entity, Boolean recibir) {
        return (T) getRepository().save(entity);
    }

    @Transactional
    public Boolean deleteById(Long id) {
        try {
            getRepository().deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public Boolean delete(T entity) {
        try {
            getRepository().delete(entity);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public List<T> saveAll(List<T> entityList) {
        return (List<T>) getRepository().saveAll(entityList);
    }

    public Long count() {
        return getRepository().count();
    }

}