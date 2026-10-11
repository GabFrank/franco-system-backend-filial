package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.service.financiero.FacturaService;
import com.franco.dev.service.financiero.PoliticaFacturacion;
import com.franco.dev.service.financiero.PoliticaFacturacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Venta + Ticket" cuando la factura no se puede crear (issue #169). {@code FacturaService}
 * devuelve null si corto una validacion previa a escribir: antes eso terminaba en un NPE al
 * imprimir el ticket-factura y el cliente se iba sin ningun comprobante, sin que el turno del
 * contador volviera. Ahora sale el ticket simple.
 */
class VentaGraphQLFacturaEImpresionTest {

    private static final Long PDV = 3L;

    @Mock private FacturaService facturaService;
    @Mock private FacturaLegalGraphQL facturaLegalGraphQL;
    @Mock private PoliticaFacturacionService politicaFacturacionService;
    @Spy @InjectMocks private VentaGraphQL ventaGraphQL;

    private final Venta venta = new Venta();
    private final Cobro cobro = new Cobro();
    private final List<VentaItem> items = Collections.singletonList(new VentaItem());
    private final ByteArrayOutputStream ticketCliente = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // En produccion la impresion del ticket-factura revienta con la factura nula.
        doThrow(new NullPointerException("factura nula")).when(facturaLegalGraphQL)
                .printTicket58mmFactura(any(), isNull(), any(), any(), any());
        doReturn(true).when(ventaGraphQL).printTicket58mm(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    private static PoliticaFacturacion intervaloQueRespeta() {
        return new PoliticaFacturacion(ConfiguracionFacturacion.MODO_INTERVALO, 3, true,
                PoliticaFacturacion.Origen.GLOBAL);
    }

    private static PoliticaFacturacion todas() {
        return new PoliticaFacturacion(ConfiguracionFacturacion.MODO_TODAS, 0, false,
                PoliticaFacturacion.Origen.GLOBAL);
    }

    private void facturaNoCreada() {
        when(facturaService.crearFacturaLegalDesdeVenta(any(), any(), any(), any())).thenReturn(null);
    }

    private void facturar(boolean credito, PoliticaFacturacion politica) throws Exception {
        ventaGraphQL.facturarEImprimir(venta, cobro, items, null, "CAJA", "local", PDV, credito, politica,
                ticketCliente);
    }

    private void verificarTicketSimple() throws Exception {
        verify(ventaGraphQL, times(1)).printTicket58mm(eq(venta), eq(cobro), eq(items), isNull(), eq(false),
                eq("CAJA"), eq("local"), eq(false), isNull(), isNull(), eq(ticketCliente));
        verify(facturaLegalGraphQL, never()).printTicket58mmFactura(any(), any(), any(), any(), any());
    }

    @Test
    void sinFactura_saleElTicketSimple() throws Exception {
        facturaNoCreada();

        facturar(false, todas());

        verificarTicketSimple();
    }

    @Test
    void sinFacturaYLaVentaTomoUnTurno_elTurnoVuelve() throws Exception {
        facturaNoCreada();

        facturar(false, intervaloQueRespeta());

        verify(politicaFacturacionService, times(1)).devolverTurno();
        verificarTicketSimple();
    }

    @Test
    void sinFacturaYLaVentaNoTomoTurno_elContadorNoSeToca() throws Exception {
        facturaNoCreada();

        facturar(false, todas());

        verify(politicaFacturacionService, never()).devolverTurno();
    }

    @Test
    void sinFacturaEnVentaACredito_saleElTicketSimpleYElContadorNoSeToca() throws Exception {
        facturaNoCreada();

        facturar(true, intervaloQueRespeta());

        verify(politicaFacturacionService, never()).devolverTurno();
        verificarTicketSimple();
    }

    /** Una falla que no es de validacion ya dejo la venta rollback-only: ni papel ni turno. */
    @Test
    void laFacturaFallaDespuesDeEscribir_noSaleTicketNiVuelveElTurno() throws Exception {
        when(facturaService.crearFacturaLegalDesdeVenta(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("fallo el insert"));

        assertThrows(IllegalStateException.class, () -> facturar(false, intervaloQueRespeta()));

        verify(ventaGraphQL, never()).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(politicaFacturacionService, never()).devolverTurno();
    }

    @Test
    void conFactura_saleElTicketFactura() throws Exception {
        FacturaLegal factura = new FacturaLegal();
        when(facturaService.crearFacturaLegalDesdeVenta(any(), any(), any(), any())).thenReturn(factura);

        facturar(false, intervaloQueRespeta());

        verify(facturaLegalGraphQL, times(1)).printTicket58mmFactura(eq(venta), eq(factura), isNull(), eq("CAJA"),
                eq(ticketCliente));
        verify(ventaGraphQL, never()).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(politicaFacturacionService, never()).devolverTurno();
    }

    /** El turno se devuelve antes de imprimir: si la impresora falla, igual volvio. */
    @Test
    void sinFacturaYElTicketSimpleFalla_elTurnoYaVolvio() throws Exception {
        facturaNoCreada();
        doThrow(new IllegalStateException("sin impresora")).when(ventaGraphQL).printTicket58mm(any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> facturar(false, intervaloQueRespeta()));

        verify(politicaFacturacionService, times(1)).devolverTurno();
    }

    @Test
    void sinFacturaConIntervaloQueNoRespeta_elContadorNoSeToca() throws Exception {
        facturaNoCreada();

        facturar(false, new PoliticaFacturacion(ConfiguracionFacturacion.MODO_INTERVALO, 3, false,
                PoliticaFacturacion.Origen.GLOBAL));

        verify(politicaFacturacionService, never()).devolverTurno();
        verificarTicketSimple();
    }

    /** La factura existe y lo que falla es su impresion: se relanza, sin ticket simple (no cambia). */
    @Test
    void conFacturaYLaImpresionFalla_seRelanzaSinTicketSimple() throws Exception {
        FacturaLegal factura = new FacturaLegal();
        when(facturaService.crearFacturaLegalDesdeVenta(any(), any(), any(), any())).thenReturn(factura);
        doThrow(new IllegalStateException("sin papel")).when(facturaLegalGraphQL)
                .printTicket58mmFactura(any(), eq(factura), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> facturar(false, intervaloQueRespeta()));

        verify(ventaGraphQL, never()).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(politicaFacturacionService, never()).devolverTurno();
    }
}
