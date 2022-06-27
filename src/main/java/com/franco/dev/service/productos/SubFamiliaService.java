package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.repository.productos.SubFamiliaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SubFamiliaService extends CrudService<Subfamilia, SubFamiliaRepository> {

    @Autowired
    private final SubFamiliaRepository repository;

    @Autowired
    private FamiliaService familiaService;

    @Override
    public SubFamiliaRepository getRepository() {
        return repository;
    }

    public List<Subfamilia> findByDescripcion(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByDescripcion(texto);
    }

    public List<Subfamilia> findByFamiliaDescripcion(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByFamiliaDescripcion(texto);
    }

    public List<Subfamilia> findByFamiliaId(Long id){
        return repository.findByFamiliaId(id);
    }

    public List<Subfamilia> findBySubFamilia(Long id){
        return repository.findBySubfamiliaId(id);
    }

    @Override
    public Subfamilia save(Subfamilia entity) {
        Subfamilia e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public List<Subfamilia> findBySubfamiliaIsNull(){
        return repository.findBySubfamiliaIsNull();
    }

}
