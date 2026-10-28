package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.service.sorteo.SorteoParticipacionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VentaResolverTieneSorteoTest {

    @Test
    void delegaEnElServicioPorId() {
        SorteoParticipacionService service = mock(SorteoParticipacionService.class);
        when(service.tieneParticipaciones(5L)).thenReturn(true);
        VentaResolver resolver = new VentaResolver();
        ReflectionTestUtils.setField(resolver, "sorteoParticipacionService", service);
        Venta v = new Venta();
        v.setId(5L);
        assertTrue(resolver.tieneSorteo(v));
        Venta otra = new Venta();
        otra.setId(6L);
        assertFalse(resolver.tieneSorteo(otra));
    }

    @Test
    void sinVentaOSinIdEsFalsoYNoConsulta() {
        SorteoParticipacionService service = mock(SorteoParticipacionService.class);
        VentaResolver resolver = new VentaResolver();
        ReflectionTestUtils.setField(resolver, "sorteoParticipacionService", service);
        assertFalse(resolver.tieneSorteo(null));
        assertFalse(resolver.tieneSorteo(new Venta()));
        verifyNoInteractions(service);
    }
}
