package com.franco.dev.service.productos.pdv;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.pdv.PdvCategoria;
import com.franco.dev.repository.productos.FamiliaRepository;
import com.franco.dev.repository.productos.pdv.PdvCategoriaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PdvCategoriaService extends CrudService<PdvCategoria, PdvCategoriaRepository> {

    private final PdvCategoriaRepository repository;

    @Override
    public PdvCategoriaRepository getRepository() {
        return repository;
    }

    public List<PdvCategoria> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto);
    }

    @Override
    public List<PdvCategoria> findAll2() {
        return repository.findAllSortByPosition();
    }

    @Override
    public PdvCategoria save(PdvCategoria entity) {
        PdvCategoria e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}
