package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.VentaItemRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class VentaItemService extends CrudService<VentaItem, VentaItemRepository> {
    private final VentaItemRepository repository;
    @Autowired
    MovimientoStockService movimientoStockService;

    @Autowired
    private Environment env;

    @Override
    public VentaItemRepository getRepository() {
        return repository;
    }

    //    public List<VentaItem> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//
    public List<VentaItem> findByVentaId(Long id) {
        return repository.findByVentaId(id);
    }

    @Override
    public VentaItem save(VentaItem entity) {
        if(entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        VentaItem e = super.save(entity);
        if (entity.getActivo() == false && entity.getId() != null) {
            MovimientoStock movimientoStock = movimientoStockService.findByTipoMovimientoAndReferencia(TipoMovimiento.VENTA, entity.getId());
            if (movimientoStock != null) {
                movimientoStock.setEstado(false);
                movimientoStockService.saveAndSend(movimientoStock, false);
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
            movimientoStock.setSucursalId(e.getSucursalId());
            movimientoStockService.saveAndSend(movimientoStock, false);
        }

//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public VentaItem saveAndSend(VentaItem entity, Boolean recibir) {
        if(entity.getPrecio()==null) entity.setPrecio(entity.getPrecioVenta().getPrecio());
        if(entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        VentaItem e = super.save(entity);
        propagacionService.propagarEntidad(e, TipoEntidad.VENTA_ITEM, recibir);
        if (entity.getActivo() == false && entity.getId() != null) {
            MovimientoStock movimientoStock = movimientoStockService.findByTipoMovimientoAndReferencia(TipoMovimiento.VENTA, entity.getId());
            if (movimientoStock != null) {
                movimientoStock.setEstado(false);
                movimientoStockService.saveAndSend(movimientoStock, recibir);
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
            movimientoStock.setSucursalId(e.getSucursalId());
            movimientoStockService.saveAndSend(movimientoStock, recibir);
        }

//        personaPublisher.publish(p);
        return e;
    }
}