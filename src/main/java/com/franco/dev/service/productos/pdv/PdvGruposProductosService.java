package com.franco.dev.service.productos.pdv;

import com.franco.dev.domain.productos.pdv.PdvGrupo;
import com.franco.dev.domain.productos.pdv.PdvGruposProductos;
import com.franco.dev.repository.productos.pdv.PdvGrupoRepository;
import com.franco.dev.repository.productos.pdv.PdvGruposProductosRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PdvGruposProductosService extends CrudService<PdvGruposProductos, PdvGruposProductosRepository> {

    private final PdvGruposProductosRepository repository;

    @Override
    public PdvGruposProductosRepository getRepository() {
        return repository;
    }

    public List<PdvGruposProductos> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto);
    }

    public List<PdvGruposProductos> findByGrupoId(Long id){
        return repository.findByPdvGrupoId(id);
    }

    public List<PdvGruposProductos> findByProductoId(Long id){
        return repository.findByProductoId(id);
    }

    public List<PdvGruposProductos> findByGrupoIdProductoId(Long id1, Long id2){
        return repository.findByPdvGrupoIdAndProductoId(id1, id2);
    }

    @Override
    public PdvGruposProductos save(PdvGruposProductos entity) {
        PdvGruposProductos e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}
