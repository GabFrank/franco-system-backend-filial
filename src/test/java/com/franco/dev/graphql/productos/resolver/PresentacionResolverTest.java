package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.PrecioEspecialLector;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.utils.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PresentacionResolverTest {

    @Mock private ImageService imageService;
    @Mock private CodigoService codigoService;
    @Mock private PrecioPorSucursalService precioPorSucursalService;
    @Mock private PrecioEspecialLector precioEspecialLector;
    @InjectMocks private PresentacionResolver resolver;

    @BeforeEach
    void setUp() { MockitoAnnotations.initMocks(this); }

    private static PrecioPorSucursal precio(long id, double valor, boolean principal) {
        PrecioPorSucursal p = new PrecioPorSucursal();
        p.setId(id);
        p.setPrecio(valor);
        p.setPrincipal(principal);
        p.setActivo(true);
        return p;
    }

    @Test
    void preciosPasaPorElLectorYPrincipalSaleDeLaListaResuelta() {
        Presentacion pr = new Presentacion();
        pr.setId(50L);
        List<PrecioPorSucursal> globales = Arrays.asList(precio(11L, 7000, false), precio(10L, 6000, true));
        List<PrecioPorSucursal> resueltos = Arrays.asList(precio(11L, 7000, false), precio(10L, 5000, true));
        when(precioPorSucursalService.findByPresentacionId(50L)).thenReturn(globales);
        when(precioEspecialLector.aplicar(globales)).thenReturn(resueltos);

        assertSame(resueltos, resolver.precios(pr));
        PrecioPorSucursal principal = resolver.precioPrincipal(pr);
        assertEquals(10L, principal.getId().longValue());
        assertEquals(5000.0, principal.getPrecio().doubleValue());
        verify(precioPorSucursalService, never()).findPrincipalByPrecionacionId(anyLong());
    }
}
