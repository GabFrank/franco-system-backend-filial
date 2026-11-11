package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Venta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Reglas de la venta en la moneda del PDV (V111.5), las mismas que aplica el escritorio: el monto en
 * la moneda de la venta por la cotizacion (unidades de la moneda de la empresa por unidad de la de la
 * venta) da el equivalente en la moneda de la empresa, en BigDecimal y con un solo redondeo a sus
 * decimales. La filial no corrige lo que manda el PDV: lo compara y, si no da, lo deja en el log.
 */
public final class MonedaVenta {

    private MonedaVenta() {
    }

    /** Decimales de una moneda; 0 sin dato (el comportamiento de los guaranies). */
    public static int decimales(Moneda moneda) {
        return moneda != null && moneda.getDecimales() != null && moneda.getDecimales() >= 0 ? moneda.getDecimales() : 0;
    }

    /** true si la moneda es otra que la de la empresa (null en cualquiera de las dos = no). */
    public static boolean esOtraMoneda(Moneda moneda, Moneda empresa) {
        return moneda != null && empresa != null && !Objects.equals(moneda.getId(), empresa.getId());
    }

    /** El equivalente en la moneda de la empresa; null si falta el monto o la cotizacion no es positiva. */
    public static BigDecimal equivalente(BigDecimal monto, BigDecimal cotizacion, int decimalesEmpresa) {
        if (monto == null || cotizacion == null || cotizacion.signum() <= 0) return null;
        return monto.multiply(cotizacion).setScale(decimalesEmpresa, RoundingMode.HALF_UP);
    }

    /**
     * true si el equivalente que mando el PDV coincide con el calculado, con {@code unidades} de la
     * moneda de la empresa de tolerancia (el PDV redondea por item; la suma puede correrse una unidad
     * por item). Sin dato para comparar, true.
     */
    public static boolean coincide(Double enviado, BigDecimal calculado, int decimalesEmpresa, int unidades) {
        if (enviado == null || calculado == null) return true;
        BigDecimal tolerancia = BigDecimal.ONE.movePointLeft(decimalesEmpresa).multiply(BigDecimal.valueOf(Math.max(unidades, 1)));
        return BigDecimal.valueOf(enviado).subtract(calculado).abs().compareTo(tolerancia) <= 0;
    }

    /**
     * El envio de un delivery (tarifa en la moneda de la empresa) en la moneda de la venta, redondeado a sus
     * decimales como lo cobra el PDV: 5.000 / 6.450 = 0,78. Null si la venta no tiene moneda o cotizacion positiva.
     */
    public static BigDecimal envioEnMoneda(Double valor, Venta venta) {
        if (valor == null || venta == null || venta.getMoneda() == null) return null;
        BigDecimal cot = venta.getCotizacion();
        if (cot == null || cot.signum() <= 0) return null;
        return BigDecimal.valueOf(valor).divide(cot, decimales(venta.getMoneda()), RoundingMode.HALF_UP);
    }

    /**
     * El envio que se cobro, en la moneda de la empresa: el de {@link #envioEnMoneda} por la cotizacion (0,78 x 6.450 =
     * 5.031). Sin moneda en la venta, el valor de la tarifa, como siempre (#254).
     */
    public static Double envioCobrado(Double valor, Venta venta, int decimalesEmpresa) {
        BigDecimal enMoneda = envioEnMoneda(valor, venta);
        if (enMoneda == null) return valor;
        return equivalente(enMoneda, venta.getCotizacion(), decimalesEmpresa).doubleValue();
    }

    public static BigDecimal aBigDecimal(Double d) {
        return d != null ? BigDecimal.valueOf(d) : null;
    }
}
