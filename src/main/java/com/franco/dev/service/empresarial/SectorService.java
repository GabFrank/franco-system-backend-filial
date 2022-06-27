package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.Sector;
import com.franco.dev.repository.empresarial.SectorRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuracion.LocalService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class SectorService extends CrudService<Sector, SectorRepository> {

    @Autowired
    private Environment environment;

    @Autowired
    private SectorRepository repository;

    @Autowired
    private LocalService localService;

    @Override
    public SectorRepository getRepository() {
        return repository;
    }

    public List<Sector> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public List<Sector> findBySucursalId(Long id) {
        return repository.findBySucursalId(id);
    }

    @Override
    public List<Sector> findAll(Pageable pageable) {
        return repository.findAllByOrderByIdAsc();
    }

    @Override
    public Sector save(Sector entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        Sector e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

}