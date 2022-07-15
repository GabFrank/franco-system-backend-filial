package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.InventarioEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.print.operaciones.MovimientoPrintService;
import com.franco.dev.repository.operaciones.InventarioRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.ProductoService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class InventarioService extends CrudService<Inventario, InventarioRepository> {

    private final Logger log = LoggerFactory.getLogger(InventarioService.class);

    private final InventarioRepository repository;

    @Autowired
    private MovimientoStockService movimientoStockService;
    @Autowired
    private MovimientoPrintService movimientoPrintService;
    @Autowired
    private InventarioProductoItemService inventarioProductoItemService;
    @Autowired
    private InventarioProductoService inventarioProductoService;
    @Autowired
    private ProductoService productoService;

    @Override
    public InventarioRepository getRepository() {
        return repository;
    }

    @Override
    public Inventario save(Inventario entity) {
        if (entity.getFechaInicio() == null) entity.setFechaInicio(LocalDateTime.now());
        Inventario e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public List<Inventario> findByDate(String inicio, String fin) {
        return repository.findByDate(inicio, fin);
    }

    public List<Inventario> findByUsuario(Long id) {
        return repository.findByUsuarioId(id);
    }

    public Inventario finalizarInventario(Long id) throws GraphQLException {
        Inventario inventario = findById(id).orElse(null);
        if (inventario.getId() != null) {
            inventario.setEstado(InventarioEstado.CONCLUIDO);
            inventario.setFechaFin(LocalDateTime.now());
            inventario = save(inventario);
            List<InventarioProducto> inventarioProductoList = inventarioProductoService.findByInventarioId(id);
            List<MovimientoStock> movimientoStockList = new ArrayList<>();
            for (InventarioProducto ip : inventarioProductoList) {
                List<InventarioProductoItem> inventarioProductoItemList = inventarioProductoItemService.findByInventarioProductoId(ip.getId());
                for (InventarioProductoItem ipi : inventarioProductoItemList) {
                    MovimientoStock movimientoStockEncontrado = null;
                    for (MovimientoStock ms : movimientoStockList) {
                        if (ipi.getPresentacion().getProducto().getId() == ms.getProducto().getId()) {
                            ms.setCantidad(ms.getCantidad() + (ipi.getPresentacion().getCantidad() * ipi.getCantidad()));
                            movimientoStockEncontrado = ms;
                        }
                    }
                    if (movimientoStockEncontrado == null) {
                        movimientoStockEncontrado = new MovimientoStock();
                        movimientoStockEncontrado.setCantidad(ipi.getCantidad() * ipi.getPresentacion().getCantidad());
                        movimientoStockEncontrado.setTipoMovimiento(TipoMovimiento.AJUSTE);
                        movimientoStockEncontrado.setReferencia(id);
                        movimientoStockEncontrado.setProducto(ipi.getPresentacion().getProducto());
                        movimientoStockEncontrado.setEstado(true);
                        movimientoStockList.add(movimientoStockEncontrado);
                    }
                }
            }
            for (MovimientoStock ms : movimientoStockList) {
                Double stockSistema = Double.valueOf(movimientoStockService.stockByProductoId(ms.getProducto().getId()));
                Double stockFisico = ms.getCantidad();
                Double diferencia = stockFisico - stockSistema; //9 - 10 = -1, 11 - 10 = 1
                ms.setCantidad(diferencia);
                movimientoStockService.save(ms);
            }
        }
        return inventario;
    }

    @Transactional()
    public void finalizarInventario2(Long id) throws GraphQLException {
        Inventario inventario = findById((long) 3).orElse(null);
        List<MovimientoStock> movimientoStockList = movimientoStockService.findByReferencia((long) 3);
        List<Producto> productoList = new ArrayList<>();
        for (MovimientoStock ms : movimientoStockList) {
            if (ms.getTipoMovimiento() == TipoMovimiento.AJUSTE) {
                List<InventarioProductoItem> inventarioProductoItemList = inventarioProductoItemService.findByProductoId(ms.getProducto().getId());
                Double cantidad = 0.0;
                if (inventarioProductoItemList.size() == 1) {
                    InventarioProductoItem mayor = inventarioProductoItemList.get(0);
                    cantidad = ventasPorProductoDuranteInventario(ms.getProducto().getId(), mayor.getCreadoEn(), inventario.getFechaFin());
                    if (cantidad != null) {
                        log.info("este producto se debe actualizar");
                        log.info(ms.getProducto().getId() + " - " + ms.getProducto().getDescripcion());
                        log.info("cantidad: " + cantidad);
                        ms.setCantidad(ms.getCantidad() - cantidad);
                        movimientoStockService.save(ms);
                    }
                } else if (inventarioProductoItemList.size() > 1) {
                    InventarioProductoItem mayor = inventarioProductoItemList.get(0);
                    InventarioProductoItem menor = mayor;
                    for (InventarioProductoItem ipi : inventarioProductoItemList) {
                        if (ipi.getCreadoEn().isAfter(mayor.getCreadoEn())) {
                            mayor = ipi;
                        } else if (ipi.getCreadoEn().isBefore(menor.getCreadoEn())) {
                            menor = ipi;
                        }
                    }
                    cantidad = ventasPorProductoDuranteInventario(ms.getProducto().getId(), menor.getCreadoEn(), mayor.getCreadoEn());
                    if (cantidad == null) {
                        cantidad = ventasPorProductoDuranteInventario(ms.getProducto().getId(), mayor.getCreadoEn(), inventario.getFechaFin());
                        if (cantidad != null) {
                            log.info("este producto se debe actualizar");
                            log.info(ms.getProducto().getId() + " - " + ms.getProducto().getDescripcion());
                            log.info("cantidad: " + cantidad);
                            ms.setCantidad(ms.getCantidad() - cantidad);
                            movimientoStockService.save(ms);
                        }
                    } else {
                        log.info("este producto NO se debe actualizar");
                        productoList.add(ms.getProducto());
                    }
                }

//                for (InventarioProductoItem ipi : inventarioProductoItemList) {
//                    cantidad = cantidad + (ipi.getPresentacion().getCantidad() * ipi.getCantidad());
//                }
//                if(!cantidad.equals(ms.getCantidad())){
//                    log.info("cantidad diferente");
//                }
//                ms.setCantidad(cantidad);
//                log.info("guardando: " + ms.getProducto().getDescripcion());
//                movimientoStockService.save(ms);
            }
        }
        for(Producto p: productoList){
            System.out.println(p.getId() + " - " + p.getDescripcion());
        }
    }

    public Double ventasPorProductoDuranteInventario(Long proId, LocalDateTime productoItemCreadoEn, LocalDateTime inventarioFin) {
        return repository.ventasHechasDuranteInventarioPorProducto(proId, productoItemCreadoEn, inventarioFin);
    }

    public Double prueba(Long proId) {
        Producto producto = productoService.findById(proId).orElse(null);
        Inventario inventario = findById((long) 3).orElse(null);
        Double cantidad = 0.0;
        if (producto != null) {
            List<InventarioProductoItem> inventarioProductoItemList = inventarioProductoItemService.findByProductoId(proId);
            if (inventarioProductoItemList.size() == 1) {
                InventarioProductoItem mayor = inventarioProductoItemList.get(0);
                cantidad = ventasPorProductoDuranteInventario(proId, mayor.getCreadoEn(), inventario.getFechaFin());
                if (cantidad != null) {
                    log.info("este producto se debe actualizar");
                    log.info("cantidad: " + cantidad);
                }
            } else if (inventarioProductoItemList.size() > 1) {
                InventarioProductoItem mayor = inventarioProductoItemList.get(0);
                InventarioProductoItem menor = mayor;
                for (InventarioProductoItem ipi : inventarioProductoItemList) {
                    if (ipi.getCreadoEn().isAfter(mayor.getCreadoEn())) {
                        mayor = ipi;
                    } else if (ipi.getCreadoEn().isBefore(menor.getCreadoEn())) {
                        menor = ipi;
                    }
                }
                cantidad = ventasPorProductoDuranteInventario(proId, menor.getCreadoEn(), mayor.getCreadoEn());
                if (cantidad == null) {
                    cantidad = ventasPorProductoDuranteInventario(proId, mayor.getCreadoEn(), inventario.getFechaFin());
                    if (cantidad != null) {
                        log.info("este producto se debe actualizar");
                        log.info("cantidad: " + cantidad);
                    }
                } else {
                    log.info("este producto NO se debe actualizar");
                }
            }
        }
        return cantidad;
    }

}