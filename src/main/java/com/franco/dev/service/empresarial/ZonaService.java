package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.Zona;
import com.franco.dev.repository.empresarial.ZonaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ZonaService extends CrudService<Zona, ZonaRepository> {

    @Autowired
    private Environment environment;

    @Autowired
    private ZonaRepository repository;

    @Override
    public ZonaRepository getRepository() {
        return repository;
    }

    public List<Zona> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public List<Zona> findBySectorId(Long id) {
        return repository.findBySectorId(id);
    }

    @Override
    public List<Zona> findAll(Pageable pageable) {
        return repository.findAllByOrderByIdAsc();
    }

    @Override
    public Zona save(Zona entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        Zona e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

}