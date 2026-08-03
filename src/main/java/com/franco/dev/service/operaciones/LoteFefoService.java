package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Asignación de lotes por FEFO (First Expired, First Out).
 *
 * Decide de qué lotes sale la mercadería cuando hay una salida de stock. Es la contraparte de la
 * entrada por compra: allá el lote lo informa el operador, acá lo resuelve el sistema.
 *
 * Espejo del servicio del central, adaptado a Java 8. Se duplica en vez de compartirse porque los
 * dos backends son artefactos independientes con distinta versión de Spring Boot.
 */
@Service
@AllArgsConstructor
public class LoteFefoService {

    /** Tolerancia al comparar cantidades en punto flotante. */
    private static final double EPSILON = 0.0001;

    /**
     * Bucket del stock que existe en el agregado pero no está atribuido a ningún lote. No es un
     * lote real: no se persiste como maestro y se deriva de existencia - suma de lotes reales.
     */
    public static final String NUMERO_LOTE_SIN_TRAZAR = "SIN LOTE";

    private final MovimientoStockLoteService movimientoStockLoteService;

    /** Una porción de la cantidad total, asignada a un lote concreto. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AsignacionLote {
        private Long loteId;
        private String numeroLote;
        private Double cantidad;
    }

    /**
     * Reparte {@code cantidad} entre los lotes disponibles de un producto en una sucursal,
     * priorizando los que hay que sacar antes.
     *
     * Reglas:
     * - Ordena por fecha de retiro (o vencimiento si no hay retiro), los sin fecha al final. El
     *   orden viene resuelto por la consulta de saldo.
     * - Excluye los lotes que no están LIBERADO: así funciona el bloqueo por recall. Un lote cuyo
     *   maestro todavía no replicó tiene estado null y también queda afuera.
     * - Si el saldo por lote no alcanza para cubrir la cantidad, asigna lo que hay y devuelve
     *   menos. La operación NO se bloquea: el stock agregado sigue siendo la fuente de verdad del
     *   total y el desglose por lote es un subconjunto trazable. Esto es lo que evita que una caja
     *   se trabe por stock histórico cargado antes de que el producto tuviera control de lote.
     *
     * @return las asignaciones, en orden FEFO. Vacío si no hay nada disponible.
     */
    public List<AsignacionLote> asignar(Long productoId, Long sucursalId, Double cantidad) {
        return asignar(productoId, sucursalId, cantidad, null);
    }

    /**
     * Igual que {@link #asignar(Long, Long, Double)}, pero saltando lotes ya comprometidos.
     *
     * Se usa para completar el faltante cuando el operador eligió lotes a mano y no alcanzaron:
     * sin la exclusión, FEFO volvería a proponer el mismo lote que ya se contó.
     *
     * @param lotesExcluidos ids a ignorar. Null o vacío se comporta igual que el método de 3
     *                       parámetros.
     */
    public List<AsignacionLote> asignar(Long productoId, Long sucursalId, Double cantidad,
                                        Set<Long> lotesExcluidos) {
        List<AsignacionLote> asignaciones = new ArrayList<>();
        if (productoId == null || sucursalId == null || cantidad == null || cantidad <= 0) {
            return asignaciones;
        }

        List<StockLoteDto> disponibles = movimientoStockLoteService.stockPorLote(productoId, sucursalId);
        double pendiente = cantidad;

        // Primera pasada: solo lotes reales, en orden FEFO.
        for (StockLoteDto lote : disponibles) {
            if (pendiente <= EPSILON) {
                break;
            }
            if (lote.getLoteId() == null || lote.getEstado() != EstadoLote.LIBERADO) {
                continue;
            }
            if (lotesExcluidos != null && lotesExcluidos.contains(lote.getLoteId())) {
                continue;
            }
            Double saldo = lote.getCantidadDisponible();
            if (saldo == null || saldo <= 0) {
                continue;
            }
            double aTomar = Math.min(saldo, pendiente);
            asignaciones.add(new AsignacionLote(lote.getLoteId(), lote.getNumeroLote(), aTomar));
            pendiente -= aTomar;
        }

        // Segunda pasada: el stock sin trazar. Va SIEMPRE al final, en pasada aparte y no
        // confiando en el ORDER BY, para que ningun cambio futuro en la consulta pueda hacer que
        // se consuma antes que un lote con vencimiento.
        if (pendiente > EPSILON) {
            for (StockLoteDto lote : disponibles) {
                if (lote.getLoteId() != null
                        || !NUMERO_LOTE_SIN_TRAZAR.equals(lote.getNumeroLote())) {
                    continue;
                }
                Double saldo = lote.getCantidadDisponible();
                if (saldo == null || saldo <= 0) {
                    continue;
                }
                double aTomar = Math.min(saldo, pendiente);
                asignaciones.add(new AsignacionLote(null, NUMERO_LOTE_SIN_TRAZAR, aTomar));
                pendiente -= aTomar;
                break;
            }
        }

        return asignaciones;
    }

    /**
     * Resuelve el reparto respetando primero los lotes que eligió el operador y completando el
     * faltante por FEFO.
     *
     * Reglas:
     * - Cada preferencia se recorta al saldo real de ese lote en la sucursal. El cajero puede pedir
     *   100 de un lote que ya solo tiene 40; se toman 40 y el resto se busca en otro lado.
     * - Las preferencias sobre lotes que no están LIBERADO se descartan: un lote bloqueado por
     *   recall no sale ni aunque lo pidan explícitamente.
     * - Lo que quede pendiente se completa por FEFO, excluyendo los lotes ya usados.
     * - Sin preferencias es exactamente {@link #asignar(Long, Long, Double)}. Ese es el camino por
     *   el que pasa toda venta que no elige lote, que es la mayoría.
     *
     * @param preferencias reparto pedido por el operador, en orden de prioridad.
     */
    public List<AsignacionLote> asignarConPreferencia(Long productoId, Long sucursalId, Double cantidad,
                                                      List<AsignacionLote> preferencias) {
        if (preferencias == null || preferencias.isEmpty()) {
            return asignar(productoId, sucursalId, cantidad);
        }
        if (productoId == null || sucursalId == null || cantidad == null || cantidad <= 0) {
            return new ArrayList<>();
        }

        Map<Long, StockLoteDto> saldoPorLote = new HashMap<>();
        for (StockLoteDto lote : movimientoStockLoteService.stockPorLote(productoId, sucursalId)) {
            if (lote.getLoteId() != null) {
                saldoPorLote.put(lote.getLoteId(), lote);
            }
        }

        List<AsignacionLote> asignaciones = new ArrayList<>();
        Set<Long> usados = new HashSet<>();
        double pendiente = cantidad;

        for (AsignacionLote preferida : preferencias) {
            if (pendiente <= EPSILON) {
                break;
            }
            if (preferida == null || preferida.getLoteId() == null || preferida.getCantidad() == null) {
                continue;
            }
            if (!usados.add(preferida.getLoteId())) {
                continue;
            }
            StockLoteDto disponible = saldoPorLote.get(preferida.getLoteId());
            if (disponible == null || disponible.getEstado() != EstadoLote.LIBERADO) {
                continue;
            }
            Double saldo = disponible.getCantidadDisponible();
            if (saldo == null || saldo <= 0) {
                continue;
            }
            double aTomar = Math.min(Math.min(saldo, preferida.getCantidad()), pendiente);
            if (aTomar <= 0) {
                continue;
            }
            asignaciones.add(new AsignacionLote(preferida.getLoteId(), disponible.getNumeroLote(), aTomar));
            pendiente -= aTomar;
        }

        if (pendiente > EPSILON) {
            asignaciones.addAll(asignar(productoId, sucursalId, pendiente, usados));
        }

        return asignaciones;
    }
}
