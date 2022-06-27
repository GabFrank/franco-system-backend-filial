package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.repository.operaciones.NotaPedidoRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NotaRecepcionService extends CrudService<NotaRecepcion, NotaRecepcionRepository> {
    private final NotaRecepcionRepository repository;

    @Override
    public NotaRecepcionRepository getRepository() {
        return repository;
    }

    public List<NotaRecepcion> findByPedidoId(Long id){
        return  repository.findByPedidoId(id);
    }

    @Override
    public NotaRecepcion save(NotaRecepcion entity) {
        NotaRecepcion e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}