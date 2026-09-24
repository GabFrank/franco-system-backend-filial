package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;

import java.util.List;

/**
 * El descuento y el aumento de una venta, en guaranies. El PDV no los guarda en la venta
 * ({@code venta.total_gs} queda bruto) sino como lineas del cobro con {@code descuento} o
 * {@code aumento} en true. El ticket simple, la factura silenciosa y la factura con DE los leen de
 * aca, para que los tres comprobantes digan lo mismo.
 */
public final class AjusteCobro {

    public static final AjusteCobro SIN_AJUSTE = new AjusteCobro(0.0, 0.0);

    private final double descuento;
    private final double aumento;

    private AjusteCobro(double descuento, double aumento) {
        this.descuento = descuento;
        this.aumento = aumento;
    }

    public static AjusteCobro deDetalles(List<CobroDetalle> detalles) {
        if (detalles == null) {
            return SIN_AJUSTE;
        }
        double descuento = 0.0;
        double aumento = 0.0;
        for (CobroDetalle cd : detalles) {
            if (Boolean.TRUE.equals(cd.getDescuento())) {
                descuento += enGuaranies(cd.getValor(), cd.getCambio());
            }
            if (Boolean.TRUE.equals(cd.getAumento())) {
                aumento += enGuaranies(cd.getValor(), cd.getCambio());
            }
        }
        return new AjusteCobro(descuento, aumento);
    }

    public static AjusteCobro deInputs(List<CobroDetalleInput> inputs) {
        if (inputs == null) {
            return SIN_AJUSTE;
        }
        double descuento = 0.0;
        double aumento = 0.0;
        for (CobroDetalleInput cdi : inputs) {
            if (Boolean.TRUE.equals(cdi.getDescuento())) {
                descuento += enGuaranies(cdi.getValor(), cdi.getCambio());
            }
            if (Boolean.TRUE.equals(cdi.getAumento())) {
                aumento += enGuaranies(cdi.getValor(), cdi.getCambio());
            }
        }
        return new AjusteCobro(descuento, aumento);
    }

    /**
     * Hay 343 descuentos de 2023-02 a 2024-01 guardados con {@code cambio} NULL, todos en guaranies:
     * NULL cuenta como 1 para que reimprimir o facturar esas ventas no reviente.
     */
    private static double enGuaranies(Double valor, Double cambio) {
        if (valor == null) {
            return 0.0;
        }
        return valor * (cambio != null ? cambio : 1.0);
    }

    public double getDescuento() {
        return descuento;
    }

    public double getAumento() {
        return aumento;
    }

    /** Descuento menos aumento: positivo es descuento neto, negativo es recargo neto. */
    public double getNeto() {
        return descuento - aumento;
    }
}
