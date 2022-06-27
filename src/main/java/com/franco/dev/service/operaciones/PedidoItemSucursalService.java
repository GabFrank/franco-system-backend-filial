package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoItemSucursal;
import com.franco.dev.repository.operaciones.PedidoItemRepository;
import com.franco.dev.repository.operaciones.PedidoItemSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PedidoItemSucursalService extends CrudService<PedidoItemSucursal, PedidoItemSucursalRepository> {
    private final PedidoItemSucursalRepository repository;

    @Override
    public PedidoItemSucursalRepository getRepository() {
        return repository;
    }

    //public List<PedidoItemSucursal> findByAll(String texto){
    //    texto = texto.replace(' ', '%');
    //    return  repository.findByAll(texto);
    //}

    public List<PedidoItemSucursal> findByPedidoItemId(Long id) { return repository.findByPedidoItemId(id); }
//
//    public List<PedidoItemSucursal> findByPedidoId(Long id) { return repository.findByPedidoId(id); }

    @Override
    public PedidoItemSucursal save(PedidoItemSucursal entity) {
        PedidoItemSucursal e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}