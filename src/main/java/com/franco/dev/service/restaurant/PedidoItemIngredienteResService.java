package com.franco.dev.service.restaurant;

import com.franco.dev.domain.restaurant.PedidoItemIngredienteRes;
import com.franco.dev.domain.restaurant.PedidoItemRes;
import com.franco.dev.repository.restaurant.PedidoItemIngredienteResRepository;
import com.franco.dev.repository.restaurant.PedidoItemResRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PedidoItemIngredienteResService extends CrudService<PedidoItemIngredienteRes, PedidoItemIngredienteResRepository> {

    @Autowired
    private final PedidoItemIngredienteResRepository repository;

    @Override
    public PedidoItemIngredienteResRepository getRepository() {
        return repository;
    }

    public List<PedidoItemIngredienteRes> findByPedidoItemResId(Long id){ return repository.findByPedidoItemResId(id) ;}

    @Override
    public PedidoItemIngredienteRes save(PedidoItemIngredienteRes entity) {
        PedidoItemIngredienteRes p = super.save(entity);
        return p;
    }

}
