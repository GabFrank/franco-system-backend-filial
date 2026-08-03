package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLotePresentacionDto;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.repository.operaciones.MovimientoStockLoteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Acceso al ledger de stock por lote. Responsabilidad única: leer saldos y mantener el desglose de
 * un movimiento agregado.
 *
 * No decide de qué lote sale la mercadería — eso es de {@link LoteFefoService} — ni conoce la
 * venta. Mantenerlo así evita el ciclo de dependencias con FEFO, que a su vez lee de acá.
 */
@Service
@AllArgsConstructor
public class MovimientoStockLoteService extends CrudService<MovimientoStockLote, MovimientoStockLoteRepository> {

    private final MovimientoStockLoteRepository repository;

    @Override
    public MovimientoStockLoteRepository getRepository() {
        return repository;
    }

    /**
     * Saldo por lote de un producto en una sucursal, en orden FEFO. Devuelve todos los estados:
     * quien descuenta stock debe filtrar por LIBERADO.
     */
    public List<StockLoteDto> stockPorLote(Long productoId, Long sucursalId) {
        if (productoId == null || sucursalId == null) {
            return new ArrayList<>();
        }
        return repository.stockPorLote(productoId, sucursalId);
    }

    /**
     * Saldo por lote convertido a una presentación concreta, para que el cajero elija en la unidad
     * con la que cobra y no en unidades base.
     *
     * La conversión se hace acá y no en el frontend por regla del proyecto: el cliente es solo capa
     * de presentación. Se devuelven presentaciones COMPLETAS más las unidades sobrantes, porque una
     * caja no se parte: mostrar "2,7 cajas" no le sirve a nadie en el mostrador.
     *
     * Solo devuelve lotes LIBERADO: lo que no se puede vender no se le ofrece al cajero.
     */
    public Page<StockLotePresentacionDto> stockPorLoteEnPresentacion(Long productoId, Long sucursalId,
                                                                     Presentacion presentacion,
                                                                     String numeroLote, Pageable pageable) {
        List<StockLotePresentacionDto> resultado = new ArrayList<>();
        // Sin presentación las cantidades quedan en unidades, igual que en el central.
        double porPresentacion = presentacion != null && presentacion.getCantidad() != null
                && presentacion.getCantidad() > 0
                ? presentacion.getCantidad()
                : 1.0;
        String filtro = numeroLote != null ? numeroLote.trim().toUpperCase() : null;

        for (StockLoteDto lote : stockPorLote(productoId, sucursalId)) {
            if (lote.getEstado() != EstadoLote.LIBERADO) {
                continue;
            }
            if (filtro != null && !filtro.isEmpty()
                    && (lote.getNumeroLote() == null || !lote.getNumeroLote().contains(filtro))) {
                continue;
            }
            double disponible = lote.getCantidadDisponible() != null ? lote.getCantidadDisponible() : 0.0;
            if (disponible <= 0) {
                continue;
            }
            double completas = Math.floor(disponible / porPresentacion);
            double sobrantes = disponible - (completas * porPresentacion);

            StockLotePresentacionDto dto = new StockLotePresentacionDto();
            dto.setLoteId(lote.getLoteId());
            dto.setNumeroLote(lote.getNumeroLote());
            dto.setFechaVencimiento(lote.getFechaVencimiento());
            dto.setFechaRetiro(lote.getFechaRetiro());
            dto.setEstado(lote.getEstado());
            dto.setCantidadDisponible(disponible);
            dto.setCantidadDisponiblePresentacion(completas);
            dto.setUnidadesSobrantes(sobrantes);
            dto.setUnidadesPorPresentacion(porPresentacion);
            dto.setPresentacionDescripcion(presentacion != null ? presentacion.getDescripcion() : null);
            resultado.add(dto);
        }
        return paginar(resultado, pageable);
    }

    /**
     * Pagina en memoria. Es correcto acá porque la lista es el saldo por lote de UN producto en UNA
     * sucursal: son unidades o decenas de filas, no un listado abierto. Traer todo y cortar evita
     * una segunda consulta de conteo en el camino del cobro.
     */
    private Page<StockLotePresentacionDto> paginar(List<StockLotePresentacionDto> filas, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(filas);
        }
        int desde = (int) pageable.getOffset();
        if (desde >= filas.size()) {
            return new PageImpl<>(new ArrayList<>(), pageable, filas.size());
        }
        int hasta = Math.min(desde + pageable.getPageSize(), filas.size());
        return new PageImpl<>(new ArrayList<>(filas.subList(desde, hasta)), pageable, filas.size());
    }

    public List<MovimientoStockLote> desglosePorMovimiento(Long movimientoStockId, Long sucursalId) {
        if (movimientoStockId == null || sucursalId == null) {
            return new ArrayList<>();
        }
        return repository.findByMovimientoStockIdAndSucursalId(movimientoStockId, sucursalId);
    }

    /**
     * Borra el desglose de un movimiento y lo confirma contra la base.
     *
     * Hay que llamarlo ANTES de calcular una asignacion nueva para ese mismo movimiento. El saldo
     * por lote se deriva del ledger, y el ledger todavia contiene las filas de la corrida
     * anterior: sin este borrado previo, el movimiento se descuenta a si mismo del saldo y la
     * asignacion se calcula contra un stock que no existe.
     *
     * El caso concreto: {@code VentaItemService} re-ejecuta {@code registrarSalidaVenta} sobre el
     * mismo movimiento cuando se edita un item ya vendido. Sin este borrado previo, la venta se
     * descontaba a si misma del saldo por lote y la atribucion salia mal.
     */
    @Transactional
    public void limpiarDesglose(MovimientoStock movimiento) {
        if (movimiento == null || movimiento.getId() == null) {
            return;
        }
        repository.deleteByMovimientoStockIdAndSucursalId(
                movimiento.getId(), movimiento.getSucursalId());
        // Flush explicito: la consulta de saldo tiene que ver la base ya sin estas filas.
        repository.flush();
    }

    /**
     * Reemplaza por completo el desglose de un movimiento.
     *
     * Se borra y se vuelve a insertar en vez de ajustar las filas existentes: cuando cambia la
     * cantidad del ítem, el reparto entre lotes deja de valer entero y recalcularlo es más simple
     * y más difícil de romper que reconciliarlo. El DELETE se replica igual que el INSERT porque la
     * tabla tiene REPLICA IDENTITY FULL.
     */
    @Transactional
    public List<MovimientoStockLote> reemplazarDesglose(Long movimientoStockId, Long sucursalId,
                                                        List<MovimientoStockLote> nuevas) {
        if (movimientoStockId == null || sucursalId == null) {
            return new ArrayList<>();
        }
        repository.deleteByMovimientoStockIdAndSucursalId(movimientoStockId, sucursalId);
        if (nuevas == null || nuevas.isEmpty()) {
            return new ArrayList<>();
        }
        return repository.saveAll(nuevas);
    }

    /**
     * Apaga o reenciende el desglose de un movimiento, en espejo del estado del padre.
     *
     * Es lo que reemplaza al ON DELETE CASCADE del central: la filial anula ventas con soft delete
     * (estado = false) y un UPDATE no dispara el cascade. Sin esto, anular una venta devolvería el
     * stock agregado pero no el stock por lote, y los dos números quedarían contando distinto.
     */
    @Transactional
    public int cambiarEstadoPorMovimiento(Long movimientoStockId, Long sucursalId, Boolean estado) {
        if (movimientoStockId == null || sucursalId == null || estado == null) {
            return 0;
        }
        List<MovimientoStockLote> hijas = repository.findByMovimientoStockIdAndSucursalId(movimientoStockId, sucursalId);
        int cambiadas = 0;
        for (MovimientoStockLote hija : hijas) {
            if (!estado.equals(hija.getEstado())) {
                hija.setEstado(estado);
                cambiadas++;
            }
        }
        if (cambiadas > 0) {
            repository.saveAll(hijas);
        }
        return cambiadas;
    }
}
