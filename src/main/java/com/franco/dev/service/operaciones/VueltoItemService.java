package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.VueltoItem;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.VueltoItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class VueltoItemService extends CrudService<VueltoItem, VueltoItemRepository> {
    private final VueltoItemRepository repository;

    @Override
    public VueltoItemRepository getRepository() {
        return repository;
    }

    public List<VueltoItem> findByVueltoId(Long id){
        return repository.findByVueltoId(id);
    }

    @Override
    public VueltoItem saveAndSend(VueltoItem entity, Boolean recibir) {
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        VueltoItem e = super.save(entity);
        propagacionService.propagarEntidad(e, TipoEntidad.VUELTO_ITEM, recibir);
        return e;
    }

    @Override
    public VueltoItem save(VueltoItem entity) {
        VueltoItem e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}