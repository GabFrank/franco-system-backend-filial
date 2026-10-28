package com.franco.dev.service.sorteo;

import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;

import java.util.List;
import java.util.Set;

/** Formula unica del sorteo: monto computable de una venta y tickets que le corresponden. */
public final class SorteoCalculo {

    private SorteoCalculo() {
    }

    /**
     * total menos descuentos (en valor absoluto), sin sumar recargos, y menos lo pagado con tarjeta
     * si el sorteo no incluye tarjeta. Nunca negativo.
     */
    public static double montoComputable(double totalGs, List<CobroDetalleInput> detalles,
                                         Set<Long> formaPagoTarjetaIds, boolean incluyeTarjeta) {
        double monto = totalGs;
        if (detalles != null) {
            for (CobroDetalleInput d : detalles) {
                if (d == null || d.getValor() == null) continue;
                double enGs = d.getValor() * (d.getCambio() != null ? d.getCambio() : 1.0);
                if (Boolean.TRUE.equals(d.getDescuento())) {
                    monto -= Math.abs(enGs);
                } else if (!incluyeTarjeta && Boolean.TRUE.equals(d.getPago()) && formaPagoTarjetaIds != null
                        && d.getFormaPagoId() != null && formaPagoTarjetaIds.contains(d.getFormaPagoId())) {
                    monto -= enGs;
                }
            }
        }
        return Math.max(0, monto);
    }

    public static int tickets(String modo, double monto, double minimo, Integer cantidadFija, Integer tope) {
        if (modo == null || monto < minimo) return 0;
        switch (modo) {
            case "UNA_POR_VENTA":
                return 1;
            case "POR_MULTIPLO": {
                if (minimo <= 0) return 0;
                int n = (int) Math.floor(monto / minimo);
                return tope != null ? Math.min(n, tope) : n;
            }
            case "CANTIDAD_FIJA":
                return Math.max(1, cantidadFija != null ? cantidadFija : 1);
            default:
                return 0;
        }
    }
}
