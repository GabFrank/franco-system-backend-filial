package com.franco.dev.service.financiero.builder;

import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class IvaResolverTest {

    private Logger log;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
    }

    private Producto producto(Long id, Integer iva) {
        Producto p = new Producto();
        p.setId(id);
        p.setIva(iva);
        return p;
    }

    private VentaItem ventaItem(Long id, Producto p) {
        VentaItem vi = new VentaItem();
        vi.setId(id);
        vi.setProducto(p);
        return vi;
    }

    private Presentacion presentacion(Long id, Producto p) {
        Presentacion pr = new Presentacion();
        pr.setId(id);
        pr.setProducto(p);
        return pr;
    }

    private boolean huboWarn(Logger log) {
        return Mockito.mockingDetails(log).getInvocations().stream()
                .anyMatch(inv -> inv.getMethod().getName().equals("warn"));
    }

    @Test
    void resuelveDesdeInput_siNoNull() {
        Integer iva = IvaResolver.resolveIva(5, producto(1L, 10), null, null, "X", null, log);
        assertEquals(Integer.valueOf(5), iva);
        assertFalse(huboWarn(log));
    }

    @Test
    void priorizaProductoSobreVentaItem() {
        Integer iva = IvaResolver.resolveIva(null, producto(1L, 0), ventaItem(99L, producto(99L, 10)), null, "X", null, log);
        assertEquals(Integer.valueOf(0), iva);
        assertFalse(huboWarn(log));
    }

    @Test
    void fallbackVentaItemProducto() {
        Integer iva = IvaResolver.resolveIva(null, null, ventaItem(99L, producto(99L, 5)), null, "X", null, log);
        assertEquals(Integer.valueOf(5), iva);
        assertFalse(huboWarn(log));
    }

    @Test
    void fallbackPresentacionProducto() {
        Integer iva = IvaResolver.resolveIva(null, null, null, presentacion(1L, producto(2L, 0)), "X", null, log);
        assertEquals(Integer.valueOf(0), iva);
        assertFalse(huboWarn(log));
    }

    @Test
    void descMatchUnico_resuelve() {
        List<Producto> matches = Collections.singletonList(producto(1L, 5));
        Integer iva = IvaResolver.resolveIva(null, null, null, null, "PROD", matches, log);
        assertEquals(Integer.valueOf(5), iva);
        assertTrue(huboWarn(log));
    }

    @Test
    void descMatchMultipleMismoIva_resuelve() {
        // Caso DURACELL AA X 4: dos productos con misma desc (uno con trailing space), ambos iva 10
        List<Producto> matches = Arrays.asList(producto(1L, 10), producto(2L, 10));
        Integer iva = IvaResolver.resolveIva(null, null, null, null, "DURACELL AA X 4", matches, log);
        assertEquals(Integer.valueOf(10), iva);
        // hay warn porque se resolvio por descripcion (consensus)
        assertTrue(huboWarn(log));
    }

    @Test
    void descMatchMultipleIvaDistinto_default10_warn() {
        List<Producto> matches = Arrays.asList(producto(1L, 5), producto(2L, 10));
        Integer iva = IvaResolver.resolveIva(null, null, null, null, "AMBIGUO", matches, log);
        assertEquals(Integer.valueOf(10), iva);
        assertTrue(huboWarn(log));
    }

    @Test
    void sinMatch_default10_warn_legacyFriendly() {
        Integer iva = IvaResolver.resolveIva(null, null, null, null, "INVENTADO XYZ", Collections.emptyList(), log);
        assertEquals(Integer.valueOf(10), iva);
        assertTrue(huboWarn(log));
    }

    @Test
    void productoSinIva_caeABuscarDescripcion() {
        Producto pSinIva = producto(1L, null);
        List<Producto> matches = Collections.singletonList(producto(99L, 5));
        Integer iva = IvaResolver.resolveIva(null, pSinIva, null, null, "X", matches, log);
        assertEquals(Integer.valueOf(5), iva);
    }
}
