package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.VentaItem;

/**
 * Precio unitario bruto que se cobro en un item de venta.
 * <p>
 * {@code venta_item.precio} es lo que el POS cobro. {@code precioVenta.precio} es el precio de
 * lista VIGENTE: puede haber cambiado despues de la venta, o no coincidir con lo cobrado si la
 * sucursal tenia un precio especial. Solo las ventas viejas sin {@code precio} caen a la lista.
 * El descuento no se resta aca: cada ticket lo sigue restando como antes.
 */
public final class PrecioCobrado {

    private PrecioCobrado() {
    }

    public static Double de(VentaItem vi) {
        if (vi == null) return null;
        if (vi.getPrecio() != null) return vi.getPrecio();
        return vi.getPrecioVenta() != null ? vi.getPrecioVenta().getPrecio() : null;
    }
}
