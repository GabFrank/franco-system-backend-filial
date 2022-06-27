package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.repository.operaciones.NotaRecepcionItemRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NotaRecepcionItemService extends CrudService<NotaRecepcionItem, NotaRecepcionItemRepository> {
    private final NotaRecepcionItemRepository repository;

    @Override
    public NotaRecepcionItemRepository getRepository() {
        return repository;
    }

    public List<NotaRecepcionItem> findByNotaRecepcionId(Long id){
        return  repository.findByNotaRecepcionId(id);
    }

    public List<NotaRecepcionItem> findByPedidoIdAndPedidoItemIsNull(Long id){
        return repository.findByPedidoIdAndPedidoItemIsNull(id);
    }

    @Override
    public NotaRecepcionItem save(NotaRecepcionItem entity) {
        NotaRecepcionItem e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}