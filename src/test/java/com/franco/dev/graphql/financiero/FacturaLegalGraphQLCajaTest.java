package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.builder.BuildRequest;
import com.franco.dev.service.financiero.builder.FacturaLegalBuilder;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** La factura del dialogo del PDV guarda su caja: sin ella, reimprimirla pide ANALISIS DE CAJA al cajero. */
class FacturaLegalGraphQLCajaTest {

    @Mock private AuditorUsuarioId auditorUsuarioId;
    @Mock private FacturaLegalService service;
    @Mock private ClienteService clienteService;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private FacturaLegalBuilder facturaLegalBuilder;

    @InjectMocks private FacturaLegalGraphQL facturaLegalGraphQL;

    @Mock private VentaService ventaService;

    private final PdvCaja caja = new PdvCaja();
    private Long ventaId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        com.franco.dev.service.seguridad.AuditorUsuarioIdMocks.firmarElInput(auditorUsuarioId);
        caja.setId(1185L);
        Cliente cliente = new Cliente();
        cliente.setId(5L);
        when(clienteService.findById(5L)).thenReturn(Optional.of(cliente));
        when(pdvCajaService.findById(1185L)).thenReturn(Optional.of(caja));
        when(facturaLegalBuilder.build(any(BuildRequest.class)))
                .thenAnswer(inv -> ((BuildRequest) inv.getArgument(0)).getFacturaLegal());
    }

    private FacturaLegal emitirCon(Long cajaId) {
        FacturaLegalInput input = new FacturaLegalInput();
        input.setCajaId(cajaId);
        input.setVentaId(ventaId);
        input.setClienteId(5L);
        input.setRuc("80000000-1");
        input.setNombre("CLIENTE");
        facturaLegalGraphQL.saveFacturaLegal(input, Collections.emptyList(), null, 25, false);
        ArgumentCaptor<BuildRequest> req = ArgumentCaptor.forClass(BuildRequest.class);
        verify(facturaLegalBuilder).build(req.capture());
        return req.getValue().getFacturaLegal();
    }

    @Test
    void conCajaLaFacturaQuedaAtadaAEsaCaja() {
        assertSame(caja, emitirCon(1185L).getCaja());
    }

    @Test
    void sinCajaQuedaSinCaja() {
        assertNull(emitirCon(null).getCaja());
        verify(pdvCajaService, never()).findById(any());
    }

    @Test
    void unaCajaQueNoExisteNoRechazaLaEmision() {
        assertNull(emitirCon(999L).getCaja());
    }

    /** Con venta, la caja de la venta (la reimpresion la sigue controlando su dueño, como antes). */
    @Test
    void conVentaMandaLaCajaDeLaVenta() {
        PdvCaja cajaDeLaVenta = new PdvCaja();
        cajaDeLaVenta.setId(1200L);
        Venta venta = new Venta();
        venta.setId(20L);
        venta.setSucursalId(24L);
        venta.setCaja(cajaDeLaVenta);
        when(ventaService.findById(20L)).thenReturn(Optional.of(venta));
        ventaId = 20L;

        assertSame(cajaDeLaVenta, emitirCon(1185L).getCaja());
    }
}
