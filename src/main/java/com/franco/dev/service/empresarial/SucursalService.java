package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.repository.empresarial.SucursalRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuracion.LocalService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SucursalService extends CrudService<Sucursal, SucursalRepository> {

    @Autowired
    private Environment environment;

    @Autowired
    private SucursalRepository repository;

    @Autowired
    private LocalService localService;

    @Override
    public SucursalRepository getRepository() {
        return repository;
    }

    public List<Sucursal> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    @Override
    public List<Sucursal> findAll(Pageable pageable) {
        return repository.findAllByOrderByIdAsc();
    }

    @Override
    public Sucursal save(Sucursal entity) {
        Sucursal e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public Sucursal sucursalActual() {
        return findById(Long.valueOf(environment.getProperty("sucursalId"))).orElse(null);
    }
}