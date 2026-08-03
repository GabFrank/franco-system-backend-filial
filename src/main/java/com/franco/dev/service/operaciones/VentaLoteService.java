package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.dto.LotePreferidoDto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.operaciones.LoteFefoService.AsignacionLote;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Traduce una venta a movimientos del ledger por lote.
 *
 * Es la contraparte de la entrada por compra del central: allá el operador informa el lote, acá el
 * sistema lo resuelve por FEFO y el cajero solo interviene si quiere forzar uno.
 *
 * Vive separado de {@code VentaItemService} para no engordar el camino crítico del POS con lógica
 * de lotes, y separado de {@link MovimientoStockLoteService} para que ese siga siendo solo acceso a
 * datos y no se arme un ciclo con {@link LoteFefoService}.
 */
@Slf4j
@Service
@AllArgsConstructor
public class VentaLoteService {

    private static final double EPSILON = 0.0001;

    private final LoteFefoService loteFefoService;
    private final MovimientoStockLoteService movimientoStockLoteService;

    /**
     * Escribe el desglose por lote de una venta, reemplazando el que hubiera.
     *
     * Decisiones deliberadas:
     * - Solo actúa si el producto tiene control de lote. Para el resto no se escribe nada y el
     *   comportamiento histórico queda intacto.
     * - Las cantidades van NEGATIVAS y en unidades base, para que se sumen con las entradas por
     *   compra en la misma unidad y el saldo del lote salga de un SUM directo.
     * - Si FEFO no logra cubrir toda la cantidad, se registra el faltante contra el bucket
     *   sin trazar ("SIN LOTE"), de modo que SUM(filas hijas) = padre se sigue cumpliendo.
     *   El saldo negativo resultante es la deuda de trazabilidad. La venta nunca se bloquea
     *   por datos de lote incompletos. Se deja un warning para poder detectarlo después.
     *
     * @param preferencias lotes elegidos a mano por el cajero, con la cantidad EN UNIDADES BASE,
     *                     la misma unidad del ledger. El selector del POS muestra unidades, así que
     *                     lo que eligió el cajero llega sin conversión de por medio.
     *                     Null o vacío = FEFO puro, que es el camino de la enorme mayoría de las
     *                     ventas.
     */
    @Transactional
    public List<MovimientoStockLote> registrarSalidaVenta(VentaItem item, MovimientoStock movimiento,
                                                          List<LotePreferidoDto> preferencias) {
        List<MovimientoStockLote> creadas = new ArrayList<>();
        if (item == null || movimiento == null || movimiento.getId() == null) {
            return creadas;
        }

        Producto producto = item.getProducto();
        if (producto == null || producto.getId() == null || !Boolean.TRUE.equals(producto.getLote())) {
            return creadas;
        }

        Long sucursalId = movimiento.getSucursalId();
        double cantidadEnUnidades = cantidadEnUnidadesBase(item);
        if (sucursalId == null || cantidadEnUnidades <= 0) {
            return creadas;
        }

        List<AsignacionLote> asignaciones = loteFefoService.asignarConPreferencia(
                producto.getId(), sucursalId, cantidadEnUnidades,
                aAsignaciones(preferencias));

        double asignado = 0.0;
        for (AsignacionLote asignacion : asignaciones) {
            if (asignacion.getCantidad() == null || asignacion.getCantidad() <= 0) {
                continue;
            }
            creadas.add(construirSalida(item, movimiento, sucursalId, asignacion));
            asignado += asignacion.getCantidad();
        }

        LoteFefoService.AsignacionLote sinTrazar =
                faltanteSinTrazar(cantidadEnUnidades, asignado);
        if (sinTrazar != null) {
            // Queda anotada en las DOS cuentas, con lo que SUM(hijas) = padre se sigue
            // cumpliendo. El saldo negativo resultante es la deuda de trazabilidad.
            creadas.add(construirSalida(item, movimiento, sucursalId, sinTrazar));
            log.warn("Venta item {} producto {}: {} de {} unidades salieron sin lote asignado. "
                            + "Se registran como {}.",
                    item.getId(), producto.getId(), sinTrazar.getCantidad(), cantidadEnUnidades,
                    LoteFefoService.NUMERO_LOTE_SIN_TRAZAR);
        }

        return movimientoStockLoteService.reemplazarDesglose(movimiento.getId(), sucursalId, creadas);
    }

    /**
     * Lo que FEFO no logró cubrir sale igual, pero anotado contra el bucket sin trazar. Antes
     * esto solo se logueaba y la venta quedaba registrada en el agregado y no en el ledger de
     * lotes, que es lo que separaba las dos cuentas para siempre.
     *
     * @return la asignación por el faltante, o null si no faltó nada.
     */
    static LoteFefoService.AsignacionLote faltanteSinTrazar(double requerida, double asignada) {
        double faltante = requerida - asignada;
        if (faltante <= EPSILON) {
            return null;
        }
        return new LoteFefoService.AsignacionLote(
                null, LoteFefoService.NUMERO_LOTE_SIN_TRAZAR, faltante);
    }

    /**
     * Espeja en las filas hijas el soft delete del movimiento agregado. Ver
     * {@link MovimientoStockLoteService#cambiarEstadoPorMovimiento}.
     */
    @Transactional
    public void cambiarEstado(MovimientoStock movimiento, Boolean estado) {
        if (movimiento == null || movimiento.getId() == null) {
            return;
        }
        movimientoStockLoteService.cambiarEstadoPorMovimiento(movimiento.getId(), movimiento.getSucursalId(), estado);
    }

    /**
     * La venta guarda la cantidad en presentaciones; el ledger vive en unidades base. Es la misma
     * conversión que hace {@code VentaItemService} al armar el movimiento agregado, y mantenerla
     * idéntica es lo que sostiene el invariante SUM(hijas) = padre.
     */
    private double cantidadEnUnidadesBase(VentaItem item) {
        if (item.getCantidad() == null) {
            return 0.0;
        }
        return item.getCantidad() * unidadesPorPresentacion(item);
    }

    private double unidadesPorPresentacion(VentaItem item) {
        return item.getPresentacion() != null && item.getPresentacion().getCantidad() != null
                && item.getPresentacion().getCantidad() > 0
                ? item.getPresentacion().getCantidad()
                : 1.0;
    }

    /**
     * Las preferencias ya vienen en unidades base: el selector del POS muestra y envía unidades,
     * que es la unidad en la que razona FEFO y en la que vive el ledger. Acá solo se cambia de
     * forma, no de unidad.
     *
     * Que no haya conversión es deliberado. Antes el cajero elegía en presentaciones y este método
     * multiplicaba por el factor; con cantidades que no eran múltiplo de la presentación, ese ida
     * y vuelta arrastraba un redondeo que desviaba el reparto entre lotes.
     */
    private List<AsignacionLote> aAsignaciones(List<LotePreferidoDto> preferencias) {
        if (preferencias == null || preferencias.isEmpty()) {
            return null;
        }
        List<AsignacionLote> convertidas = new ArrayList<>();
        for (LotePreferidoDto preferida : preferencias) {
            if (preferida == null || preferida.getLoteId() == null || preferida.getCantidad() == null) {
                continue;
            }
            convertidas.add(new AsignacionLote(preferida.getLoteId(), null, preferida.getCantidad()));
        }
        return convertidas.isEmpty() ? null : convertidas;
    }

    private MovimientoStockLote construirSalida(VentaItem item, MovimientoStock movimiento,
                                                Long sucursalId, AsignacionLote asignacion) {
        MovimientoStockLote salida = new MovimientoStockLote();
        salida.setSucursalId(sucursalId);
        salida.setMovimientoStockId(movimiento.getId());
        salida.setProducto(item.getProducto());
        salida.setPresentacion(item.getPresentacion());
        salida.setNumeroLote(asignacion.getNumeroLote());
        // Negativa: es una salida. El saldo del lote es SUM(cantidad) sobre el ledger.
        salida.setCantidad(-asignacion.getCantidad());
        salida.setReferencia(item.getId());
        salida.setEstado(true);
        salida.setUsuarioId(item.getUsuario() != null ? item.getUsuario().getId() : null);
        salida.setCreadoEn(item.getCreadoEn() != null ? item.getCreadoEn() : LocalDateTime.now());
        salida.setLote(referenciaLote(asignacion.getLoteId()));
        return salida;
    }

    /**
     * Solo se necesita la FK, así que se arma una referencia con el id en vez de traer el lote
     * entero de la base. Evita una consulta por cada porción asignada dentro del cobro.
     */
    private Lote referenciaLote(Long loteId) {
        if (loteId == null) {
            return null;
        }
        Lote lote = new Lote();
        lote.setId(loteId);
        return lote;
    }
}
