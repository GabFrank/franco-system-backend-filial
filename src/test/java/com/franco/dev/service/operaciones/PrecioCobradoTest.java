package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PrecioCobradoTest {

    static VentaItem item(Double cobrado, Double lista) {
        VentaItem vi = new VentaItem();
        vi.setPrecio(cobrado);
        if (lista != null) {
            PrecioPorSucursal p = new PrecioPorSucursal();
            p.setPrecio(lista);
            vi.setPrecioVenta(p);
        }
        return vi;
    }

    @Test
    public void usaLoCobradoAunqueLaListaSeaOtra() {
        assertEquals(5000.0, PrecioCobrado.de(item(5000.0, 6000.0)).doubleValue());
    }

    @Test
    public void ventaViejaSinPrecioCobradoCaeALaLista() {
        assertEquals(6000.0, PrecioCobrado.de(item(null, 6000.0)).doubleValue());
    }

    @Test
    public void sinNingunoDevuelveNull() {
        assertNull(PrecioCobrado.de(item(null, null)));
        assertNull(PrecioCobrado.de(null));
    }
}
