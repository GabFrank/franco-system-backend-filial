package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Compra;
import com.franco.dev.repository.operaciones.CompraRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CompraService extends CrudService<Compra, CompraRepository> {
    private final CompraRepository repository;

    @Override
    public CompraRepository getRepository() {
        return repository;
    }

    //public List<Compra> findByAll(String texto){
    //    texto = texto.replace(' ', '%');
    //    return  repository.findByAll(texto);
    //}

    public List<Compra> findByProductoId(Long id){
        return repository.findByProductoId(id);
    }

    public Compra findByPedidoId(Long id) {
        return repository.findByPedidoId(id);
    }

    @Override
    public Compra save(Compra entity) {
        Compra e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}