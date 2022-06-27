package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NecesidadItem;
import com.franco.dev.repository.operaciones.NecesidadItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NecesidadItemService extends CrudService<NecesidadItem, NecesidadItemRepository> {
    private final NecesidadItemRepository repository;

    @Override
    public NecesidadItemRepository getRepository() {
        return repository;
    }

    //public List<SolicitudCompraItem> findByAll(String texto){
    //    texto = texto.replace(' ', '%');
    //    return  repository.findByAll(texto);
    //}

    public List<NecesidadItem> findByProductoId(Long id) { return repository.findByProductoId(id); }

    public List<NecesidadItem> findByNecesidad(Long id) { return repository.findByNecesidadId(id); }

    @Override
    public NecesidadItem save(NecesidadItem entity) {
        NecesidadItem e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}