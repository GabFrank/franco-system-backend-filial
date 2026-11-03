package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Lo que el ticket de una venta en otra moneda que la de la empresa (V111.5) muestra en esa moneda:
 * la moneda y su cotizacion, el precio de cada item y el total. Una venta sin moneda (todas las de
 * bodega) no pasa por aca y el ticket sale como siempre.
 */
public final class TicketMonedaVenta {

    private static final Locale GERMAN = Locale.GERMAN;

    private TicketMonedaVenta() {
    }

    public static boolean aplica(Venta venta) {
        return venta != null && venta.getMoneda() != null && venta.getCotizacion() != null
                && venta.getCotizacion().signum() > 0;
    }

    /** "Moneda: DOLAR  Cotiz.: 6.100": la cotizacion es moneda de la empresa por unidad. */
    public static String lineaMoneda(Venta venta) {
        String nombre = venta.getMoneda().getDenominacion() != null ? venta.getMoneda().getDenominacion() : "";
        return "Moneda: " + nombre + "  Cotiz.: " + formato(venta.getCotizacion(), venta.getCotizacion().scale() > 0 ? 2 : 0);
    }

    /**
     * Precio unitario del item en la moneda de la venta, neto del descuento del item (que viaja en la
     * moneda de la empresa, como precio). Sin precio_en_moneda (item viejo), se convierte el de siempre.
     */
    public static BigDecimal unitario(VentaItem vi, Venta venta) {
        int dec = MonedaVenta.decimales(venta.getMoneda());
        BigDecimal cot = venta.getCotizacion();
        BigDecimal bruto = vi.getPrecioEnMoneda() != null ? vi.getPrecioEnMoneda()
                : BigDecimal.valueOf(PrecioCobrado.de(vi) != null ? PrecioCobrado.de(vi) : 0.0).divide(cot, dec + 4, RoundingMode.HALF_UP);
        BigDecimal descuento = vi.getValorDescuento() != null && vi.getValorDescuento() != 0
                ? BigDecimal.valueOf(vi.getValorDescuento()).divide(cot, dec + 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return bruto.subtract(descuento).setScale(dec, RoundingMode.HALF_UP);
    }

    public static BigDecimal totalItem(VentaItem vi, Venta venta) {
        int dec = MonedaVenta.decimales(venta.getMoneda());
        return unitario(vi, venta).multiply(BigDecimal.valueOf(vi.getCantidad() != null ? vi.getCantidad() : 0.0))
                .setScale(dec, RoundingMode.HALF_UP);
    }

    /**
     * Total a pagar en la moneda de la venta: el del cobro si lo trae (ya neto de descuento y aumento);
     * si no, el total final en la moneda de la empresa dividido por la cotizacion de la venta.
     */
    public static BigDecimal totalFinal(Venta venta, Cobro cobro, double totalFinalEmpresa) {
        int dec = MonedaVenta.decimales(venta.getMoneda());
        if (cobro != null && cobro.getTotalMoneda() != null) return cobro.getTotalMoneda().setScale(dec, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(totalFinalEmpresa).divide(venta.getCotizacion(), dec, RoundingMode.HALF_UP);
    }

    public static String formato(BigDecimal monto, int decimales) {
        NumberFormat f = NumberFormat.getNumberInstance(GERMAN);
        f.setMinimumFractionDigits(decimales);
        f.setMaximumFractionDigits(decimales);
        return f.format(monto != null ? monto : BigDecimal.ZERO);
    }

    public static int decimales(Venta venta) {
        return MonedaVenta.decimales(venta.getMoneda());
    }
}
