package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VentaItemResolverTest {

    @Test
    void valorTotalUsaElPrecioCobradoYNoElDeListaVigente() {
        VentaItem vi = new VentaItem();
        vi.setPrecio(5000.0);
        vi.setCantidad(2.0);
        PrecioPorSucursal lista = new PrecioPorSucursal();
        lista.setPrecio(6000.0);
        vi.setPrecioVenta(lista);
        assertEquals(10000.0, new VentaItemResolver().valorTotal(vi).doubleValue());   // el codigo viejo da 12000
    }
}
