package com.franco.dev.service.productos.pdv;

import com.franco.dev.domain.productos.pdv.PdvCategoria;
import com.franco.dev.domain.productos.pdv.PdvGrupo;
import com.franco.dev.repository.productos.pdv.PdvCategoriaRepository;
import com.franco.dev.repository.productos.pdv.PdvGrupoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PdvGrupoService extends CrudService<PdvGrupo, PdvGrupoRepository> {

    private final PdvGrupoRepository repository;

    @Override
    public PdvGrupoRepository getRepository() {
        return repository;
    }

    public List<PdvGrupo> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto);
    }

    @Override
    public PdvGrupo save(PdvGrupo entity) {
        PdvGrupo e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}
