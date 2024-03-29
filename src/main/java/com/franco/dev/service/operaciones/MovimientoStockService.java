package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MovimientoStockService extends CrudService<MovimientoStock, MovimientoStockRepository> {
    private final MovimientoStockRepository repository;
    @Autowired
    private TransferenciaItemService transferenciaItemService;

    @Override
    public MovimientoStockRepository getRepository() {
        return repository;
    }

    public Float stockByProductoIdAndSucursalId(Long proId) {
        return repository.stockByProductoId(proId) != null ? repository.stockByProductoId(proId) : 0;
    }

    public Float stockByProductoId(Long proId) {
        Float stock = repository.stockByProductoId(proId);
        return stock != null ? stock : 0;
    }

    public MovimientoStock findByProductoIdAndTIpoAndReferenciaId(Long proId, Long referenciaId) {
        return repository.findByProductoIdAndTipoMovimientoAndReferencia(proId, TipoMovimiento.AJUSTE, referenciaId);
    }

    public List<MovimientoStock> findByReferencia(Long id) {
        return repository.findByReferencia(id);
    }

    @Override
    public MovimientoStock save(MovimientoStock entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        MovimientoStock e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public MovimientoStock saveAndSend(MovimientoStock entity, Boolean recibir) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        MovimientoStock e = super.save(entity);
//        personaPublisher.publish(p);
        propagacionService.propagarEntidad(e, TipoEntidad.MOVIMIENTO_STOCK, recibir);
        return e;
    }

    public List<MovimientoStock> ultimosMovimientos(Long proId, TipoMovimiento tm, Integer limit) {
        if (tm != null) {
            tm = TipoMovimiento.COMPRA;
        }
        if (limit < 1) {
            limit = 1;
        }
        return repository.ultimosMovimientosPorProductoId(proId, tm.toString(), limit);
    }

    public MovimientoStock findByTipoMovimientoAndReferencia(TipoMovimiento tipoMovimiento, Long referencia) {
        return repository.findByTipoMovimientoAndReferencia(tipoMovimiento, referencia);
    }

    public List<MovimientoStock> findByDate(String inicio, String fin) {
        return repository.findByDate(inicio, fin);
    }

    public Boolean bajaStockPorTransferencia(Long id) {
        Boolean ok = false;
        List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaItemId(id);
        for (TransferenciaItem ti : transferenciaItemList) {
            if (ti.getMotivoRechazoPreTransferencia() == null && ti.getMotivoRechazoPreparacion() == null && ti.getMotivoRechazoTransporte() == null) {
                MovimientoStock movimientoStock = new MovimientoStock();
                movimientoStock.setEstado(true);
                movimientoStock.setCantidad(ti.getCantidadPreTransferencia() * ti.getPresentacionPreTransferencia().getCantidad() * (-1));
                movimientoStock.setProducto(ti.getPresentacionPreTransferencia().getProducto());
                movimientoStock.setReferencia(ti.getId());
                movimientoStock.setTipoMovimiento(TipoMovimiento.TRANSFERENCIA);
                saveAndSend(movimientoStock, false);
            }
            ok = true;
        }
        return ok;
    }

    public Boolean altaStockPorTransferencia(Long id) {
        Boolean ok = false;
        List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaItemId(id);
        for (TransferenciaItem ti : transferenciaItemList) {
            if (ti.getCantidadRecepcion() != null && ti.getMotivoRechazoRecepcion() == null) {
                MovimientoStock movimientoStock = new MovimientoStock();
                movimientoStock.setEstado(true);
                movimientoStock.setCantidad(ti.getCantidadRecepcion() * ti.getPresentacionRecepcion().getCantidad());
                movimientoStock.setProducto(ti.getPresentacionPreTransferencia().getProducto());
                movimientoStock.setReferencia(id);
                movimientoStock.setTipoMovimiento(TipoMovimiento.TRANSFERENCIA);
                saveAndSend(movimientoStock, false);
            }
            ok = true;
        }
        return ok;
    }
}