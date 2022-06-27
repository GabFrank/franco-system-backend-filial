package com.franco.dev.service.restaurant;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.restaurant.PedidoRes;
import com.franco.dev.repository.productos.ProductoRepository;
import com.franco.dev.repository.restaurant.PedidoResRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PedidoResService extends CrudService<PedidoRes, PedidoResRepository> {

    @Autowired
    private final PedidoResRepository repository;
//    private final PersonaPublisher personaPublisher;


    @Override
    public PedidoResRepository getRepository() {
        return repository;
    }

    @Override
    public PedidoRes save(PedidoRes entity) {
        PedidoRes p = super.save(entity);
        return p;
    }

}
