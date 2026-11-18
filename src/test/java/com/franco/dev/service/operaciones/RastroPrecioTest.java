package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La regla de conversion del precio de lista (V110.5), la misma que aplica el escritorio: lista por
 * cotizacion, un solo redondeo a los decimales de la moneda de cobro.
 */
class RastroPrecioTest {

    @Test
    void dolaresAGuaranies_redondeaUnaVezASinDecimales() {
        // 4,39 USD a 5.980 = 26.252,2 Gs -> 26.252
        assertEquals(new BigDecimal("26252"), RastroPrecio.esperado(4.39, 5980.0, 0));
    }

    @Test
    void conDecimales_redondeaHalfUp() {
        // 10.000 Gs a 1/5.980 USD = 1,6722... -> 1,67
        assertEquals(new BigDecimal("1.67"), RastroPrecio.esperado(10000.0, 1.0 / 5980.0, 2));
    }

    @Test
    void rastroIncompleto_noHayEsperado() {
        assertNull(RastroPrecio.esperado(null, 5980.0, 0));
        assertNull(RastroPrecio.esperado(4.39, null, 0));
        assertNull(RastroPrecio.esperado(4.39, 0.0, 0));
    }

    @Test
    void coincide_conUnaUnidadDeTolerancia() {
        BigDecimal e = new BigDecimal("26252");
        assertTrue(RastroPrecio.coincide(26252.0, e, 0));
        assertTrue(RastroPrecio.coincide(26253.0, e, 0));
        assertFalse(RastroPrecio.coincide(26300.0, e, 0));
        assertTrue(RastroPrecio.coincide(null, e, 0));
    }

    @Test
    void elModelMapperDeSaveVentaItem_noSeConfundeConLosCamposNuevos() {
        VentaItemInput in = new VentaItemInput();
        in.setPrecio(26252.0);
        in.setPrecioVentaId(9L);
        in.setPrecioMonedaId(3L);
        in.setPrecioLista(4.39);
        in.setPrecioCotizacion(5980.0);
        VentaItem e = new ModelMapper().map(in, VentaItem.class);
        assertEquals(26252.0, e.getPrecio());
        assertEquals(0, new BigDecimal("4.39").compareTo(e.getPrecioLista()));
    }
}
