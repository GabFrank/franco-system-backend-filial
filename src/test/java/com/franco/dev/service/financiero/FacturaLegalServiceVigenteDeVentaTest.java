package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Una venta que ya quedo con dos facturas (bug de #207) no traba la busqueda: se toma la activa mas nueva. */
class FacturaLegalServiceVigenteDeVentaTest {

    @Mock private FacturaLegalRepository repository;
    @InjectMocks private FacturaLegalService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private static FacturaLegal factura(long id, Boolean activo) {
        FacturaLegal f = new FacturaLegal();
        f.setId(id);
        f.setActivo(activo);
        return f;
    }

    @Test
    void conVariasDevuelveLaActivaMasNueva() {
        FacturaLegal anulada = factura(30, false);
        FacturaLegal vigente = factura(20, null);
        when(repository.findByVentaIdAndSucursalIdOrderByIdDesc(7L, 24L))
                .thenReturn(Arrays.asList(anulada, vigente, factura(10, true)));

        assertSame(vigente, service.findVigenteDeVenta(7L, 24L));
    }

    @Test
    void sinFacturasDevuelveNull() {
        when(repository.findByVentaIdAndSucursalIdOrderByIdDesc(7L, 24L)).thenReturn(Collections.emptyList());

        assertNull(service.findVigenteDeVenta(7L, 24L));
    }
}
