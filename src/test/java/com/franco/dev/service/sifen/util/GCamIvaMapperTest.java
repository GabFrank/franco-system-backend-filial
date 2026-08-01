package com.franco.dev.service.sifen.util;

import com.roshka.sifen.core.fields.request.de.TgCamIVA;
import com.roshka.sifen.core.types.TiAfecIVA;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GCamIvaMapperTest {

    /**
     * Regresion: un item exento dejaba dTasaIVA en null, jsifenlib lo serializaba como el
     * texto "null" y SIFEN descartaba el DE sin devolver resultado individual en el lote.
     * MT v150: para E731=3 la proporcion gravada (regla 1905) y la tasa (regla 1907) son 0.
     */
    @Test
    void exentoInformaProporcionYTasaEnCero() {
        TgCamIVA g = GCamIvaMapper.construir(0);

        assertEquals(TiAfecIVA.EXENTO, g.getiAfecIVA());
        assertEquals(0, BigDecimal.ZERO.compareTo(g.getdPropIVA()));
        assertEquals(0, BigDecimal.ZERO.compareTo(g.getdTasaIVA()));
    }

    @Test
    void gravado10InformaProporcion100YTasa10() {
        TgCamIVA g = GCamIvaMapper.construir(10);

        assertEquals(TiAfecIVA.GRAVADO, g.getiAfecIVA());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(g.getdPropIVA()));
        assertEquals(0, BigDecimal.TEN.compareTo(g.getdTasaIVA()));
    }

    @Test
    void gravado5InformaProporcion100YTasa5() {
        TgCamIVA g = GCamIvaMapper.construir(5);

        assertEquals(TiAfecIVA.GRAVADO, g.getiAfecIVA());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(g.getdPropIVA()));
        assertEquals(0, BigDecimal.valueOf(5).compareTo(g.getdTasaIVA()));
    }

    @Test
    void ivaNuloODesconocidoCaeEn10() {
        for (Integer iva : new Integer[]{null, 21}) {
            TgCamIVA g = GCamIvaMapper.construir(iva);

            assertEquals(TiAfecIVA.GRAVADO, g.getiAfecIVA());
            assertEquals(0, BigDecimal.TEN.compareTo(g.getdTasaIVA()));
        }
    }

    /**
     * Ningun campo obligatorio del grupo puede quedar en null: jsifenlib los emite con
     * String.valueOf(), asi que un null se convierte en el literal "null" y rompe el XSD.
     */
    @Test
    void ningunPorcentajeDejaCamposObligatoriosEnNull() {
        for (Integer iva : new Integer[]{null, 0, 5, 10, 21}) {
            TgCamIVA g = GCamIvaMapper.construir(iva);

            assertNotNull(g.getiAfecIVA(), "iAfecIVA nulo para iva=" + iva);
            assertNotNull(g.getdPropIVA(), "dPropIVA nulo para iva=" + iva);
            assertNotNull(g.getdTasaIVA(), "dTasaIVA nulo para iva=" + iva);
        }
    }
}
