package com.franco.dev.service.operaciones;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * La regla de conversion del precio de lista a la moneda de cobro (V110.5), la misma que aplica el
 * escritorio al agregar el item: precio de lista por cotizacion (unidades de la moneda de cobro por
 * unidad de la del precio), en BigDecimal y con un solo redondeo a los decimales de la moneda de
 * cobro. La filial no corrige lo que manda el PDV: lo compara y, si no da, lo deja en el log.
 */
public final class RastroPrecio {

    private RastroPrecio() {
    }

    /** El precio en la moneda de cobro que corresponde al rastro; null si el rastro esta incompleto. */
    public static BigDecimal esperado(Double precioLista, Double cotizacion, int decimalesCobro) {
        if (precioLista == null || cotizacion == null || !(cotizacion > 0)) return null;
        return BigDecimal.valueOf(precioLista).multiply(BigDecimal.valueOf(cotizacion))
                .setScale(decimalesCobro, RoundingMode.HALF_UP);
    }

    /** true si el precio cobrado coincide con el rastro, con una unidad de la moneda de cobro de tolerancia. */
    public static boolean coincide(Double precio, BigDecimal esperado, int decimalesCobro) {
        if (precio == null || esperado == null) return true;
        BigDecimal unidad = BigDecimal.ONE.movePointLeft(decimalesCobro);
        return BigDecimal.valueOf(precio).subtract(esperado).abs().compareTo(unidad) <= 0;
    }
}
