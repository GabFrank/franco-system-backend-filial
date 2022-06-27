package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Ingrediente;
import com.franco.dev.repository.productos.IngredienteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class IngredienteService extends CrudService<Ingrediente, IngredienteRepository> {

    @Autowired
    private final IngredienteRepository repository;

    @Override
    public IngredienteRepository getRepository() {
        return repository;
    }

    public List<Ingrediente> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByDescripcion(texto);
    }

    @Override
    public Ingrediente save(Ingrediente entity) {
        Ingrediente p = super.save(entity);
//        personaPublisher.publish(p);
        return p;
    }
}
