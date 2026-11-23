package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.service.operaciones.VentaItemService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** #265: el descuento se suma en la base, sin cargar los items (traen producto, presentacion y precio en EAGER). */
class VentaResolverValorDescuentoTest {

    @Test
    void sumaEnLaBaseSinCargarLosItems() {
        VentaItemService service = mock(VentaItemService.class);
        when(service.totalDescuentoByVentaId(5L)).thenReturn(530000.0);
        VentaResolver resolver = new VentaResolver();
        ReflectionTestUtils.setField(resolver, "ventaItemService", service);
        Venta v = new Venta();
        v.setId(5L);

        assertEquals(530000.0, resolver.valorDescuento(v));
        verify(service, never()).findByVentaId(any());
    }

    @Test
    void sinVentaOSinIdEsCeroYNoConsulta() {
        VentaItemService service = mock(VentaItemService.class);
        VentaResolver resolver = new VentaResolver();
        ReflectionTestUtils.setField(resolver, "ventaItemService", service);

        assertEquals(0.0, resolver.valorDescuento(null));
        assertEquals(0.0, resolver.valorDescuento(new Venta()));
        verifyNoInteractions(service);
    }
}
