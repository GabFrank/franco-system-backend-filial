package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.productos.PresentacionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

/** #265: el codigo principal se pide una sola vez por producto (antes, dos). */
class ProductoResolverCodigoPrincipalTest {

    private ProductoResolver resolver(PresentacionService presentacionService, PresentacionResolver presentacionResolver) {
        ProductoResolver r = new ProductoResolver();
        ReflectionTestUtils.setField(r, "presentacionService", presentacionService);
        ReflectionTestUtils.setField(r, "presentacionResolver", presentacionResolver);
        return r;
    }

    private static Producto producto(long id) {
        Producto p = new Producto();
        p.setId(id);
        return p;
    }

    @Test
    void devuelveElCodigoConUnaSolaConsulta() {
        PresentacionService presentacionService = mock(PresentacionService.class);
        PresentacionResolver presentacionResolver = mock(PresentacionResolver.class);
        Presentacion presentacion = new Presentacion();
        Codigo codigo = new Codigo();
        codigo.setCodigo("7840058000190");
        when(presentacionService.findByPrincipalAndProductoId(true, 156L)).thenReturn(presentacion);
        when(presentacionResolver.codigoPrincipal(presentacion)).thenReturn(codigo);

        assertEquals("7840058000190", resolver(presentacionService, presentacionResolver).codigoPrincipal(producto(156L)));
        verify(presentacionResolver, times(1)).codigoPrincipal(presentacion);
    }

    @Test
    void sinCodigoPrincipalEsNull() {
        PresentacionService presentacionService = mock(PresentacionService.class);
        PresentacionResolver presentacionResolver = mock(PresentacionResolver.class);
        Presentacion presentacion = new Presentacion();
        when(presentacionService.findByPrincipalAndProductoId(true, 7L)).thenReturn(presentacion);

        assertNull(resolver(presentacionService, presentacionResolver).codigoPrincipal(producto(7L)));
        verify(presentacionResolver, times(1)).codigoPrincipal(presentacion);
    }

    @Test
    void sinPresentacionPrincipalEsNullYNoBuscaCodigo() {
        PresentacionService presentacionService = mock(PresentacionService.class);
        PresentacionResolver presentacionResolver = mock(PresentacionResolver.class);

        assertNull(resolver(presentacionService, presentacionResolver).codigoPrincipal(producto(8L)));
        verifyNoInteractions(presentacionResolver);
    }
}
