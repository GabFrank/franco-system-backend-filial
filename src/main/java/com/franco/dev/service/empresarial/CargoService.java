package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.repository.empresarial.CargoRepository;
import com.franco.dev.repository.general.CiudadRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CargoService extends CrudService<Cargo, CargoRepository> {

    private final CargoRepository repository;

    @Override
    public CargoRepository getRepository() {
        return repository;
    }

    public List<Cargo> findByDescripcion(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByDescripcion(texto);
    }

    public List<Cargo> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto.toUpperCase());
    }

    @Override
    public Cargo save(Cargo entity) {
        Cargo e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}