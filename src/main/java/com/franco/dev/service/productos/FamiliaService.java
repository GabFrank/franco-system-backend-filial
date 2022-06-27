package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.repository.productos.FamiliaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class FamiliaService extends CrudService<Familia, FamiliaRepository> {

    private final FamiliaRepository repository;

    @Override
    public FamiliaRepository getRepository() {
        return repository;
    }

    public List<Familia> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto);
    }

    @Override
    public Familia save(Familia entity) {
        Familia e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}
