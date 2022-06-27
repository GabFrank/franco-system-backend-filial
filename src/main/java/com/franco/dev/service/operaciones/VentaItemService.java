package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.repository.operaciones.VentaItemRepository;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.ProductoService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaItemService extends CrudService<VentaItem, VentaItemRepository> {
    private final VentaItemRepository repository;

    @Override
    public VentaItemRepository getRepository() {
        return repository;
    }

    @Autowired
    MovimientoStockService movimientoStockService;


//    public List<VentaItem> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//
    public List<VentaItem> findByVentaId(Long id){
        return repository.findByVentaId(id);
    }

    @Override
    public VentaItem save(VentaItem entity) {
        VentaItem e = super.save(entity);
        if(entity.getActivo()==false && entity.getId()!=null){
            MovimientoStock movimientoStock = movimientoStockService.findByTipoMovimientoAndReferencia(TipoMovimiento.VENTA, entity.getId());
            if(movimientoStock!=null){
                movimientoStock.setEstado(false);
                movimientoStockService.save(movimientoStock);
            }
        } else {
            MovimientoStock movimientoStock = new MovimientoStock();
            movimientoStock.setCreadoEn(entity.getCreadoEn());
            movimientoStock.setUsuario(entity.getUsuario());
            movimientoStock.setTipoMovimiento(TipoMovimiento.VENTA);
            movimientoStock.setReferencia(e.getId());
            movimientoStock.setEstado(true);
            movimientoStock.setProducto(e.getProducto());
            movimientoStock.setCantidad(e.getCantidad() * e.getPresentacion().getCantidad() * -1);
            movimientoStock.setCreadoEn(e.getCreadoEn());
            movimientoStock.setUsuario(e.getUsuario());
            movimientoStockService.save(movimientoStock);
        }

//        personaPublisher.publish(p);
        return e;
    }
}