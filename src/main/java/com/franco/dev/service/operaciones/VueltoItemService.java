package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Vuelto;
import com.franco.dev.domain.operaciones.VueltoItem;
import com.franco.dev.repository.operaciones.VueltoItemRepository;
import com.franco.dev.repository.operaciones.VueltoRepository;
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
    public VueltoItem save(VueltoItem entity) {
        VueltoItem e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}