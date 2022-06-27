package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.CompraItemEstado;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import com.franco.dev.repository.operaciones.PedidoItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PedidoItemService extends CrudService<PedidoItem, PedidoItemRepository> {
    private final PedidoItemRepository repository;

    @Override
    public PedidoItemRepository getRepository() {
        return repository;
    }

    @Autowired
    public CompraItemService compraItemService;

    //public List<PedidoItem> findByAll(String texto){
    //    texto = texto.replace(' ', '%');
    //    return  repository.findByAll(texto);
    //}

    public List<PedidoItem> findByProductoId(Long id) { return repository.findByProductoId(id); }

    public List<PedidoItem> findByPedidoId(Long id) { return repository.findByPedidoId(id); }

    public List<PedidoItem> findByPedidoIdSobrantes(Long id){
        return repository.findByPedidoIdSobrantes(id);
    }

    public List<PedidoItem> findByNotaRecepcionId(Long id) {
        return repository.findByNotaRecepcionId(id);
    }

    @Override
    public PedidoItem save(PedidoItem entity) {
        PedidoItem e = null;
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
            e = super.save(entity);
            if(e!=null){
                CompraItem compraItem = compraItemService.findByPedidoItemId(e.getId());
                if(compraItem==null){
                    compraItem = new CompraItem();
                    compraItem.setPedidoItem(e);
                    compraItem.setPresentacion(e.getPresentacion());
                    compraItem.setProducto(e.getProducto());
                    compraItem.setBonificacion(e.getBonificacion());
                    compraItem.setCantidad(e.getCantidad());
                    compraItem.setDescuentoUnitario(e.getDescuentoUnitario());
                    compraItem.setEstado(CompraItemEstado.SIN_MODIFICACION);
                    compraItem.setPrecioUnitario(e.getPrecioUnitario());
                    compraItem.setCreadoEn(e.getCreadoEn());
                    compraItemService.save(compraItem);
                }
            }
        return e;
    }
}