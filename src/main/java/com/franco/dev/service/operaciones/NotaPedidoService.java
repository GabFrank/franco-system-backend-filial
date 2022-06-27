package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.repository.operaciones.NotaPedidoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class NotaPedidoService extends CrudService<NotaPedido, NotaPedidoRepository> {
    private final NotaPedidoRepository repository;

    @Override
    public NotaPedidoRepository getRepository() {
        return repository;
    }

//    public List<NotaPedido> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//    }

    @Override
    public NotaPedido save(NotaPedido entity) {
        NotaPedido e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}