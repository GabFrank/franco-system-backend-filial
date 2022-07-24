package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.repository.operaciones.TransferenciaItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TransferenciaItemService extends CrudService<TransferenciaItem, TransferenciaItemRepository> {
    private final TransferenciaItemRepository repository;

    @Override
    public TransferenciaItemRepository getRepository() {
        return repository;
    }

    public List<TransferenciaItem> findByTransferenciaItemId(Long id, Integer page, Integer size){
        if(page==null && size == null){
            return  repository.findByTransferenciaIdOrderByIdDesc(id);
        }
        Pageable pageable = PageRequest.of(page, size);
        return  repository.findByTransferenciaIdOrderByIdDesc(id, pageable);
    }

    public List<TransferenciaItem> findByTransferenciaItemId(Long id){
        return  repository.findByTransferenciaIdOrderByIdDesc(id);
    }

    @Override
    public TransferenciaItem save(TransferenciaItem entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        TransferenciaItem e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}