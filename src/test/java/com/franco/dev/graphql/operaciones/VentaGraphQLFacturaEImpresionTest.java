package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.graphql.financiero.VentaCreditoGraphQL;
import com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput;
import com.franco.dev.graphql.financiero.input.VentaCreditoInput;
import com.franco.dev.service.financiero.FacturaService;
import com.franco.dev.service.financiero.PoliticaFacturacion;
import com.franco.dev.service.financiero.PoliticaFacturacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
    @Mock private VentaCreditoGraphQL ventaCreditoGraphQL;
    @Spy @InjectMocks private VentaGraphQL ventaGraphQL;

    private final Venta venta = new Venta();
    private final Cobro cobro = new Cobro();
    private final List<VentaItem> items = Collections.singletonList(new VentaItem());
    private final ByteArrayOutputStream ticketCliente = new ByteArrayOutputStream();
    private final VentaCreditoInput ventaCreditoInput = new VentaCreditoInput();
    private final List<VentaCreditoCuotaInput> cuotas = Collections.singletonList(new VentaCreditoCuotaInput());

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // En produccion la impresion del ticket-factura revienta con la factura nula.
        doThrow(new NullPointerException("factura nula")).when(facturaLegalGraphQL)
                .printTicket58mmFactura(any(), isNull(), any(), any(), any(), any(), any());
        doReturn(true).when(ventaGraphQL).printTicket58mm(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        venta.setId(77L);
        venta.setSucursalId(24L);
        when(ventaCreditoGraphQL.saveVentaCredito(any(), any())).thenReturn(new VentaCredito());
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
        ventaGraphQL.facturarEImprimir(venta, cobro, items, null, "CAJA", "local", PDV,
                credito ? ventaCreditoInput : null, credito ? cuotas : null, politica, ticketCliente);
    }

    private FacturaLegal facturaCreada() {
        FacturaLegal factura = new FacturaLegal();
        when(facturaService.crearFacturaLegalDesdeVenta(any(), any(), any(), any())).thenReturn(factura);
        return factura;
    }

    /** El ticket de la venta con los pagares al pie. */
    private void verificarTicketConPagare() throws Exception {
        verify(ventaGraphQL, times(1)).printTicket58mm(eq(venta), eq(cobro), eq(items), isNull(), eq(false),
                eq("CAJA"), eq("local"), eq(true), eq(cuotas), isNull(), eq(ticketCliente));
    }

    private void verificarTicketSimple() throws Exception {
        verify(ventaGraphQL, times(1)).printTicket58mm(eq(venta), eq(cobro), eq(items), isNull(), eq(false),
                eq("CAJA"), eq("local"), eq(false), isNull(), isNull(), eq(ticketCliente));
        verify(facturaLegalGraphQL, never()).printTicket58mmFactura(any(), any(), any(), any(), any(), any(), any());
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

    /** El credito ya quedo guardado: el respaldo es el ticket con pagare, no el simple (issue #133). */
    @Test
    void sinFacturaEnVentaACredito_saleElTicketConPagareYElContadorNoSeToca() throws Exception {
        facturaNoCreada();

        facturar(true, todas());

        verify(ventaCreditoGraphQL, times(1)).saveVentaCredito(ventaCreditoInput, cuotas);
        verify(politicaFacturacionService, never()).devolverTurno();
        verificarTicketConPagare();
        verify(facturaLegalGraphQL, never()).printTicket58mmFactura(any(), any(), any(), any(), any(), any(), any());
    }

    // ---- venta a credito facturada (issue #133) ----

    /** Esta ruta facturaba sin guardar el VentaCredito ni imprimir el pagare. */
    @Test
    void creditoConFactura_guardaElCreditoYElPagareSaleAlPieDeLaFactura() throws Exception {
        FacturaLegal factura = facturaCreada();

        facturar(true, todas());

        verify(ventaCreditoGraphQL, times(1)).saveVentaCredito(ventaCreditoInput, cuotas);
        assertEquals(Long.valueOf(77L), ventaCreditoInput.getVentaId());
        assertEquals(Long.valueOf(24L), ventaCreditoInput.getSucursalId());
        InOrder orden = inOrder(ventaCreditoGraphQL, facturaService, facturaLegalGraphQL);
        orden.verify(ventaCreditoGraphQL).saveVentaCredito(any(), any());
        orden.verify(facturaService).crearFacturaLegalDesdeVenta(any(), any(), any(), any());
        orden.verify(facturaLegalGraphQL).printTicket58mmFactura(eq(venta), eq(factura), isNull(), eq("CAJA"),
                eq(ticketCliente), eq(cuotas), eq("local"));
        // Un solo papel: no hay un segundo ticket con el pagare.
        verify(ventaGraphQL, never()).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    /** De contado la factura sale sin pagare. */
    @Test
    void contadoConFactura_laFacturaSaleSinPagare() throws Exception {
        FacturaLegal factura = facturaCreada();

        facturar(false, todas());

        verify(facturaLegalGraphQL, times(1)).printTicket58mmFactura(eq(venta), eq(factura), isNull(), eq("CAJA"),
                eq(ticketCliente), isNull(), eq("local"));
    }

    /** Sin credito guardado no hay pagare, como en la ruta PAGARE_CREDITO. */
    @Test
    void creditoQueNoSeGuarda_laFacturaSaleSinPagare() throws Exception {
        FacturaLegal factura = facturaCreada();
        when(ventaCreditoGraphQL.saveVentaCredito(any(), any())).thenReturn(null);

        facturar(true, todas());

        verify(facturaLegalGraphQL, times(1)).printTicket58mmFactura(eq(venta), eq(factura), isNull(), eq("CAJA"),
                eq(ticketCliente), isNull(), eq("local"));
    }

    /** A credito el turno tambien se toma, asi que tambien se devuelve si la factura no se crea. */
    @Test
    void sinFacturaEnVentaACreditoQueTomoUnTurno_elTurnoVuelve() throws Exception {
        facturaNoCreada();

        facturar(true, intervaloQueRespeta());

        verify(politicaFacturacionService, times(1)).devolverTurno();
    }

    /** La venta se pierde antes de facturar: el turno que tomo no puede quedar consumido. */
    @Test
    void elCreditoFallaEnUnaVentaQueTomoUnTurno_elTurnoVuelve() throws Exception {
        when(ventaCreditoGraphQL.saveVentaCredito(any(), any())).thenThrow(new IllegalStateException("sin cliente"));

        assertThrows(IllegalStateException.class, () -> facturar(true, intervaloQueRespeta()));

        verify(politicaFacturacionService, times(1)).devolverTurno();
        verify(facturaService, never()).crearFacturaLegalDesdeVenta(any(), any(), any(), any());
    }

    /** Si guardar el credito falla, la venta no llega a facturarse. */
    @Test
    void elCreditoFalla_noSeFacturaNiSeImprime() throws Exception {
        when(ventaCreditoGraphQL.saveVentaCredito(any(), any())).thenThrow(new IllegalStateException("sin cliente"));

        assertThrows(IllegalStateException.class, () -> facturar(true, todas()));

        verify(politicaFacturacionService, never()).devolverTurno();
        verify(facturaService, never()).crearFacturaLegalDesdeVenta(any(), any(), any(), any());
        verify(ventaGraphQL, never()).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
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
                eq(ticketCliente), isNull(), eq("local"));
        verify(ventaGraphQL, never()).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(politicaFacturacionService, never()).devolverTurno();
        verify(ventaCreditoGraphQL, never()).saveVentaCredito(any(), any());
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
                .printTicket58mmFactura(any(), eq(factura), any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> facturar(false, intervaloQueRespeta()));

        verify(ventaGraphQL, never()).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(politicaFacturacionService, never()).devolverTurno();
    }

    /** El cajero puede corregirlo: sale para rechazar la venta, sin ticket simple; el turno vuelve. */
    @Test
    void ventaNoFacturable_seRelanzaSinTicketSimpleYElTurnoVuelve() throws Exception {
        when(facturaService.crearFacturaLegalDesdeVenta(any(), any(), any(), any()))
                .thenThrow(new com.franco.dev.service.financiero.builder.VentaNoFacturableException("cargue el cliente"));

        assertThrows(com.franco.dev.service.financiero.builder.VentaNoFacturableException.class,
                () -> facturar(false, intervaloQueRespeta()));

        verify(ventaGraphQL, never()).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(politicaFacturacionService, times(1)).devolverTurno();
    }

    // ---- factura silenciosa (issue #170) ----

    private void facturarEnSilencio(PoliticaFacturacion politica) {
        ventaGraphQL.facturarEnSilencio(new com.franco.dev.graphql.financiero.input.FacturaLegalInput(),
                Collections.emptyList(), "CAJA", PDV, politica);
    }

    private void laFacturaSilenciosaFallaCon(Exception error) {
        when(facturaLegalGraphQL.saveFacturaLegal(any(), any(), any(), any(), any())).thenThrow(error);
    }

    /** saveFacturaLegal re-envuelve lo que lanza el builder: el rechazo se busca en la causa. */
    @Test
    void silenciosa_ventaNoFacturableEnvuelta_rechazaLaVentaYElTurnoVuelve() {
        com.franco.dev.service.financiero.builder.VentaNoFacturableException rechazo =
                new com.franco.dev.service.financiero.builder.VentaNoFacturableException("cargue un cliente");
        laFacturaSilenciosaFallaCon(new graphql.GraphQLException("Error al guardar factura legal: ...", rechazo));

        com.franco.dev.service.financiero.builder.VentaNoFacturableException lanzada = assertThrows(
                com.franco.dev.service.financiero.builder.VentaNoFacturableException.class,
                () -> facturarEnSilencio(intervaloQueRespeta()));

        org.junit.jupiter.api.Assertions.assertSame(rechazo, lanzada);
        verify(politicaFacturacionService, times(1)).devolverTurno();
    }

    /** Lo que el cajero no puede corregir no rompe la venta: sigue sin factura y el turno vuelve. */
    @Test
    void silenciosa_facturaNoEmitible_noRompeLaVentaYElTurnoVuelve() {
        laFacturaSilenciosaFallaCon(new graphql.GraphQLException("Error al guardar factura legal: ...",
                new graphql.GraphQLException("FACTURA_NO_EMITIBLE: precio cero")));

        facturarEnSilencio(intervaloQueRespeta());

        verify(politicaFacturacionService, times(1)).devolverTurno();
    }

    /** Una falla posterior a un write tampoco se relanza desde aca, y el turno no vuelve. */
    @Test
    void silenciosa_otraFalla_noSeRelanzaYElTurnoNoVuelve() {
        laFacturaSilenciosaFallaCon(new graphql.GraphQLException("Error al guardar factura legal: ...",
                new IllegalStateException("fallo el insert")));

        facturarEnSilencio(intervaloQueRespeta());

        verify(politicaFacturacionService, never()).devolverTurno();
    }
}
