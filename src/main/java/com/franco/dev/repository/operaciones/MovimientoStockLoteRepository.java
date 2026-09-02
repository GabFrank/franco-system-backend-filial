package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.LoteVendidoDto;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Ledger de stock por lote. En la filial se lee para resolver FEFO y se escribe el desglose de
 * cada venta.
 */
@Repository
public interface MovimientoStockLoteRepository extends HelperRepository<MovimientoStockLote, Long> {

    default Class<MovimientoStockLote> getEntityClass() {
        return MovimientoStockLote.class;
    }

    /**
     * Desglose de un movimiento agregado. Se usa al anular una venta, para apagar las hijas junto
     * con el padre.
     */
    List<MovimientoStockLote> findByMovimientoStockIdAndSucursalId(Long movimientoStockId, Long sucursalId);

    /**
     * Borra el desglose de un movimiento para poder rehacerlo. Se usa cuando la cantidad del ítem
     * cambia y el reparto entre lotes deja de valer: recalcular es más simple y más seguro que
     * intentar ajustar las filas existentes.
     */
    void deleteByMovimientoStockIdAndSucursalId(Long movimientoStockId, Long sucursalId);

    /**
     * Saldo disponible por lote de un producto en una sucursal. Equivale a la vista
     * operaciones.v_stock_lote filtrada, ordenada por FEFO (First Expired, First Out).
     *
     * FEFO ordena por la fecha de RETIRO, no por el vencimiento: la idea es sacar la mercadería
     * antes de que efectivamente venza. Sin fecha de retiro se cae al vencimiento, y los lotes sin
     * ninguna fecha conocida quedan al final.
     *
     * Devuelve todos los estados: el filtro de LIBERADO lo aplica quien consume, para que una
     * pantalla de consulta pueda ver también lo bloqueado. Quien descuenta stock SÍ debe filtrar.
     *
     * El LEFT JOIN es deliberado: si el maestro del lote todavía no replicó, la fila igual aparece
     * con el saldo correcto y las fechas y el estado en null.
     */
    @Query("SELECT new com.franco.dev.domain.operaciones.dto.StockLoteDto(" +
            "  l.id, e.producto.id, e.sucursalId, e.numeroLote, " +
            "  l.fechaVencimiento, l.fechaRetiro, l.estado, SUM(e.cantidad)) " +
            "FROM MovimientoStockLote e LEFT JOIN e.lote l " +
            "WHERE e.estado = true AND e.producto.id = :productoId AND e.sucursalId = :sucursalId " +
            "GROUP BY l.id, e.producto.id, e.sucursalId, e.numeroLote, " +
            "  l.fechaVencimiento, l.fechaRetiro, l.estado " +
            "HAVING SUM(e.cantidad) <> 0 " +
            "ORDER BY CASE WHEN COALESCE(l.fechaRetiro, l.fechaVencimiento) IS NULL THEN 1 ELSE 0 END, " +
            "  COALESCE(l.fechaRetiro, l.fechaVencimiento) ASC, l.id ASC")
    List<StockLoteDto> stockPorLote(@Param("productoId") Long productoId,
                                    @Param("sucursalId") Long sucursalId);

    /**
     * Los lotes de los que salió cada ítem de una venta, para imprimirlos en el ticket.
     *
     * Va por lista de ítems y no de a uno porque el ticket se arma dentro del cobro: una consulta
     * por ítem serían N consultas en el camino crítico del POS.
     *
     * El JOIN contra MovimientoStock no es decorativo: {@code referencia} guarda el id del ítem de
     * ORIGEN, que según el tipo de movimiento es un venta_item o un recepcion_mercaderia_item.
     * Filtrando solo por referencia, una recepción con el mismo id que un venta_item se colaría en
     * el ticket como si fuera un lote vendido.
     *
     * Se agrupa porque un mismo lote puede aparecer en más de una fila del ledger para el mismo
     * ítem; el ticket muestra una línea por lote, no una por fila.
     *
     * La cantidad sale tal cual del ledger, o sea NEGATIVA por ser una salida; darla vuelta es
     * trabajo de quien la imprime. Se deja acá y no en la query para no meter una resta dentro de
     * una expresión de constructor, que es justo donde el parser de HQL es más quisquilloso.
     *
     * El LEFT JOIN al maestro es deliberado, igual que en {@link #stockPorLote}: si el lote todavía
     * no replicó, la línea igual se imprime con el número y sin vencimiento.
     */
    @Query("SELECT new com.franco.dev.domain.operaciones.dto.LoteVendidoDto(" +
            "  e.referencia, e.numeroLote, l.fechaVencimiento, SUM(e.cantidad)) " +
            "FROM MovimientoStockLote e " +
            "  LEFT JOIN e.lote l " +
            "  JOIN MovimientoStock ms " +
            "    ON ms.id = e.movimientoStockId AND ms.sucursalId = e.sucursalId " +
            "WHERE e.estado = true AND ms.estado = true " +
            "  AND ms.tipoMovimiento = com.franco.dev.domain.operaciones.enums.TipoMovimiento.VENTA " +
            "  AND e.sucursalId = :sucursalId AND e.referencia IN (:ventaItemIds) " +
            "GROUP BY e.referencia, e.numeroLote, l.fechaVencimiento " +
            "HAVING SUM(e.cantidad) <> 0 " +
            "ORDER BY e.referencia ASC, " +
            "  CASE WHEN l.fechaVencimiento IS NULL THEN 1 ELSE 0 END, " +
            "  l.fechaVencimiento ASC, e.numeroLote ASC")
    List<LoteVendidoDto> lotesVendidosPorItem(@Param("ventaItemIds") List<Long> ventaItemIds,
                                              @Param("sucursalId") Long sucursalId);
}
