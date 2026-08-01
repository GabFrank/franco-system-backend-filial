package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLotePresentacionDto;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.repository.operaciones.MovimientoStockLoteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
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
    public List<StockLotePresentacionDto> stockLotePorPresentacion(Long productoId, Long sucursalId,
                                                                   Presentacion presentacion) {
        List<StockLotePresentacionDto> resultado = new ArrayList<>();
        if (presentacion == null) {
            return resultado;
        }
        double porPresentacion = presentacion.getCantidad() != null && presentacion.getCantidad() > 0
                ? presentacion.getCantidad()
                : 1.0;

        for (StockLoteDto lote : stockPorLote(productoId, sucursalId)) {
            if (lote.getEstado() != EstadoLote.LIBERADO) {
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
            dto.setPresentacionDescripcion(presentacion.getDescripcion());
            resultado.add(dto);
        }
        return resultado;
    }

    public List<MovimientoStockLote> desglosePorMovimiento(Long movimientoStockId, Long sucursalId) {
        if (movimientoStockId == null || sucursalId == null) {
            return new ArrayList<>();
        }
        return repository.findByMovimientoStockIdAndSucursalId(movimientoStockId, sucursalId);
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
