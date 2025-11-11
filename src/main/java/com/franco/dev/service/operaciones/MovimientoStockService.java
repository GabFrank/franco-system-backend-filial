package com.franco.dev.service.operaciones;

import com.franco.dev.domain.dto.StockPorTipoMovimientoDto;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.StockPorProductoSucursal;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.dto.MovimientoStockCantidadAndIdDto;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MovimientoStockService extends CrudService<MovimientoStock, MovimientoStockRepository> {
    private final MovimientoStockRepository repository;
    @Autowired
    private TransferenciaItemService transferenciaItemService;

    @Autowired
    private StockPorProductoSucursalService stockPorProductoSucursalService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private Environment env;

    @Override
    public MovimientoStockRepository getRepository() {
        return repository;
    }

    public Double stockByProductoIdAndSucursalId(Long proId) {
        return Double.valueOf(stockByProductoId(proId));
    }

    public Double stockByProductoIdAndSucursalId(Long proId, Long sucId) {
        Float stock = repository.stockByProductoIdAndSucursalId(proId, sucId);
        if(stock == null) stock = Float.valueOf(0);
        return Double.valueOf(stock);
    }

    public Double stockByProductoIdExecptMovStockId(Long proId, Long movId, Long sucId) {
        Float stock = repository.stockByProductoIdExeptMovimientoId(proId, movId, sucId);
        return Double.valueOf(stock != null ? stock : 0);
    }

    public Double stockByProductoIdAndSucursalIdAntesDeFecha(Long proId, Long sucId, LocalDateTime fecha) {
        Float stock = repository.stockByProductoIdAndSucursalIdAntesDeFecha(proId, sucId, fecha);
        return Double.valueOf(stock != null ? stock : 0);
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
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MovimientoStock save(MovimientoStock entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        MovimientoStock e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MovimientoStock saveAndSend(MovimientoStock entity, Boolean recibir) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        MovimientoStock e = super.save(entity);
//        personaPublisher.publish(p);
//        propagacionService.propagarEntidad(e, TipoEntidad.MOVIMIENTO_STOCK, recibir);
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
        log.info("Entrando en alta de stock");
        Boolean ok = false;
        List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaItemId(id);
        for (TransferenciaItem ti : transferenciaItemList) {
            log.info("Item: " + ti.getPresentacionRecepcion().getProducto().getDescripcion());
            if (ti.getCantidadRecepcion() != null && ti.getMotivoRechazoRecepcion() == null) {
                MovimientoStock movimientoStock = new MovimientoStock();
                movimientoStock.setEstado(true);
                movimientoStock.setCantidad(ti.getCantidadRecepcion() * ti.getPresentacionRecepcion().getCantidad());
                movimientoStock.setProducto(ti.getPresentacionPreTransferencia().getProducto());
                movimientoStock.setReferencia(ti.getId());
                movimientoStock.setTipoMovimiento(TipoMovimiento.TRANSFERENCIA);
                log.info("movimiento creado");
                log.info(movimientoStock.toString());
                saveAndSend(movimientoStock, false);
            }
            ok = true;
        }
        return ok;
    }

    public Page<MovimientoStock> findMovimientoStockWithFilters(LocalDateTime inicio,
                                                                LocalDateTime fin,
                                                                List<Long> sucursalList,
                                                                Long productoId,
                                                                List<TipoMovimiento> tipoMovimientoList,
                                                                Long usuarioId,
                                                                Pageable pageable) {
        List<String> stringEnum = null;
        if (tipoMovimientoList != null) {
            stringEnum = tipoMovimientoList.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }

        return repository.findByFilters(inicio, fin, sucursalList, productoId, stringEnum, usuarioId, pageable);
    }

    public Double findStockWithFilters(LocalDateTime inicio,
                                       LocalDateTime fin,
                                       List<Long> sucursalList,
                                       Long productoId,
                                       List<TipoMovimiento> tipoMovimientoList,
                                       Long usuarioId) {
        List<String> stringEnum = null;
        if (tipoMovimientoList != null) {
            stringEnum = tipoMovimientoList.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }
        Double stock = repository.findStockWithFilters(inicio, fin, sucursalList, productoId, stringEnum, usuarioId);
        return stock == null ? 0 : stock;
    }

    public List<StockPorTipoMovimientoDto> findStockPorTipoMovimiento(LocalDateTime inicio,
                                                                      LocalDateTime fin,
                                                                      List<Long> sucursalList,
                                                                      Long productoId,
                                                                      List<TipoMovimiento> tipoMovimientoList,
                                                                      Long usuarioId) {
        List<String> stringEnum = null;
        if (tipoMovimientoList != null) {
            stringEnum = tipoMovimientoList.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }
        return repository.findStockPorTipoMovimiento(inicio, fin, sucursalList, productoId, stringEnum, usuarioId);
    }
}