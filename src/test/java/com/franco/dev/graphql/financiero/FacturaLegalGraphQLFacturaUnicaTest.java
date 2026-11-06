package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.builder.BuildRequest;
import com.franco.dev.service.financiero.builder.FacturaLegalBuilder;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** FINALIZAR con factura de un delivery ya facturado no emite otra (#221). */
class FacturaLegalGraphQLFacturaUnicaTest {

    @Mock private AuditorUsuarioId auditorUsuarioId;
    @Mock private FacturaLegalService service;
    @Mock private VentaService ventaService;
    @Mock private ClienteService clienteService;
    @Mock private PersonaService personaService;
    @Mock private FacturaLegalBuilder facturaLegalBuilder;

    @InjectMocks private FacturaLegalGraphQL facturaLegalGraphQL;

    private Venta venta;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        venta = new Venta();
        venta.setId(20L);
        venta.setSucursalId(24L);
        cliente = new Cliente();
        cliente.setId(5L);
        when(ventaService.findById(20L)).thenReturn(Optional.of(venta));
        when(clienteService.findById(5L)).thenReturn(Optional.of(cliente));
    }

    private static FacturaLegalInput inputConVenta() {
        FacturaLegalInput input = new FacturaLegalInput();
        input.setVentaId(20L);
        input.setClienteId(5L);
        input.setRuc("80000000-1");
        input.setNombre("CLIENTE");
        return input;
    }

    @Test
    void ventaYaFacturadaSeRechazaSinEscribirNada() {
        doThrow(new GraphQLException("La venta ya tiene la factura N° 48: no se emite otra."))
                .when(service).exigirSinFacturaVigente(20L, 24L);

        GraphQLException e = assertThrows(GraphQLException.class, () -> facturaLegalGraphQL
                .saveFacturaLegal(inputConVenta(), Collections.emptyList(), null, 25, false));

        assertTrue(e.getMessage().contains("La venta ya tiene la factura N° 48"), e.getMessage());
        verifyNoInteractions(facturaLegalBuilder, clienteService, personaService);
        verify(ventaService, never()).save(any());
    }

    @Test
    void elClienteSeAsignaALaVentaRecienDespuesDeEmitir() {
        when(facturaLegalBuilder.build(any(BuildRequest.class)))
                .thenAnswer(inv -> ((BuildRequest) inv.getArgument(0)).getFacturaLegal());

        facturaLegalGraphQL.saveFacturaLegal(inputConVenta(), Collections.emptyList(), null, 25, false);

        InOrder orden = inOrder(service, facturaLegalBuilder, ventaService);
        orden.verify(service).exigirSinFacturaVigente(20L, 24L);
        orden.verify(facturaLegalBuilder).build(any(BuildRequest.class));
        orden.verify(ventaService).save(venta);
        assertSame(cliente, venta.getCliente());
    }

    /** La factura ya se emitió: un error al guardar la venta no puede salir como rechazo (el desktop no avisaría). */
    @Test
    void siFallaGuardarLaVentaDespuesDeEmitirSeDevuelveIgual() {
        com.franco.dev.domain.financiero.TimbradoDetalle timbrado = new com.franco.dev.domain.financiero.TimbradoDetalle();
        when(facturaLegalBuilder.build(any(BuildRequest.class))).thenAnswer(inv -> {
            FacturaLegal f = ((BuildRequest) inv.getArgument(0)).getFacturaLegal();
            f.setId(100L);
            f.setTimbradoDetalle(timbrado);
            return f;
        });
        when(ventaService.save(any())).thenThrow(new RuntimeException("serializacion"));

        assertSame(timbrado, facturaLegalGraphQL.saveFacturaLegal(inputConVenta(), Collections.emptyList(), null, 25, false));
    }

    @Test
    void siLaEmisionFallaLaVentaNoQuedaConElCliente() {
        when(facturaLegalBuilder.build(any(BuildRequest.class))).thenThrow(new GraphQLException("sin timbrado"));

        assertThrows(GraphQLException.class, () -> facturaLegalGraphQL
                .saveFacturaLegal(inputConVenta(), Collections.emptyList(), null, 25, false));

        verify(ventaService, never()).save(any());
        assertNull(venta.getCliente());
    }
}
