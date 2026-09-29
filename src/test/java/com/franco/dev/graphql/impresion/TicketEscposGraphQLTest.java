package com.franco.dev.graphql.impresion;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.graphql.financiero.input.SenaCuponInput;
import com.franco.dev.graphql.operaciones.CobroGraphQL;
import com.franco.dev.graphql.operaciones.VentaGraphQL;
import com.franco.dev.graphql.operaciones.VentaItemGraphQL;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.RetiroDetalleService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.impresion.dto.GastoDto;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.impresion.dto.SenaCuponDto;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.io.OutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ticketEscpos genera, para imprimir desde el cliente, el mismo comprobante que hoy imprime la
 * reimpresion de cada cosa (reimprimirVenta, reimprimirFacturaLegal, reimprimirGasto, ...), pero
 * escrito en memoria y devuelto en base64. Nunca pide impresora: printerName va siempre null.
 */
class TicketEscposGraphQLTest {

    @Mock private VentaService ventaService;
    @Mock private VentaRepository ventaRepository;
    @Mock private FacturaLegalService facturaLegalService;
    @Mock private FacturaLegalItemService facturaLegalItemService;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private GastoService gastoService;
    @Mock private RetiroService retiroService;
    @Mock private RetiroDetalleService retiroDetalleService;
    @Mock private DeliveryService deliveryService;
    @Mock private CobroGraphQL cobroGraphQL;
    @Mock private VentaItemGraphQL ventaItemGraphQL;
    @Mock private VentaGraphQL ventaGraphQL;
    @Mock private FacturaLegalGraphQL facturaLegalGraphQL;
    @Mock private ImpresionService impresionService;
    @InjectMocks private TicketEscposGraphQL resolver;

    private static final byte[] TICKET = {0x1b, 0x40, 'O', 'K', 0x0a};
    private static final String TICKET_B64 = Base64.getEncoder().encodeToString(TICKET);

    /** Simula un renderer: escribe TICKET en el OutputStream que recibe como argumento n. */
    private static Answer<Object> escribeEnArgumento(int n) {
        return inv -> {
            ((OutputStream) inv.getArgument(n)).write(TICKET);
            return true;
        };
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(ventaService.getRepository()).thenReturn(ventaRepository);
    }

    @Test
    void ventaSinFacturaReimprimeElTicketConReimpresion() throws Exception {
        Venta venta = new Venta();
        venta.setId(80078L);
        Cobro cobro = new Cobro();
        cobro.setId(501L);
        venta.setCobro(cobro);
        when(ventaService.findById(80078L)).thenReturn(Optional.of(venta));
        when(cobroGraphQL.cobro(501L, null)).thenReturn(Optional.of(cobro));
        when(ventaItemGraphQL.ventaItemListPorVentaId(80078L, null)).thenReturn(Collections.emptyList());
        doAnswer(escribeEnArgumento(10)).when(ventaGraphQL).printTicket58mm(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(OutputStream.class));

        assertEquals(TICKET_B64, resolver.ticketEscpos(TicketEscposTipo.VENTA, 80078L, null, "CAJA 1"));
        verify(ventaGraphQL).printTicket58mm(eq(venta), eq(cobro), anyList(), anyList(), eq(true), isNull(),
                eq("CAJA 1"), isNull(), isNull(), isNull(), any(OutputStream.class));
    }

    @Test
    void ventaConFacturaReimprimeLaFactura() throws Exception {
        Venta venta = new Venta();
        venta.setId(80078L);
        FacturaLegal factura = new FacturaLegal();
        factura.setId(30044L);
        factura.setVenta(venta);
        when(ventaService.findById(80078L)).thenReturn(Optional.of(venta));
        when(facturaLegalService.findByVentaId(80078L)).thenReturn(factura);
        when(facturaLegalService.findById(30044L)).thenReturn(Optional.of(factura));
        doAnswer(escribeEnArgumento(4)).when(facturaLegalGraphQL).printTicket58mmFactura(any(), any(), any(), any(),
                any(OutputStream.class));

        assertEquals(TICKET_B64, resolver.ticketEscpos(TicketEscposTipo.VENTA, 80078L, null, null));
        verify(ventaGraphQL, never()).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(OutputStream.class));
    }

    @Test
    void facturaEnMonedaExtranjeraUsaSuRenderer() throws Exception {
        FacturaLegal factura = new FacturaLegal();
        factura.setId(30044L);
        factura.setMonedaExtranjera("DOLAR");
        factura.setTipoCambio(7000.0);
        when(facturaLegalService.findById(30044L)).thenReturn(Optional.of(factura));
        doAnswer(escribeEnArgumento(6)).when(facturaLegalGraphQL).printTicket58mmFacturaMonedaExtranjera(any(),
                any(), any(), any(), any(), any(), any(OutputStream.class));

        assertEquals(TICKET_B64, resolver.ticketEscpos(TicketEscposTipo.FACTURA, 30044L, null, null));
        verify(facturaLegalGraphQL).printTicket58mmFacturaMonedaExtranjera(isNull(), eq(factura), any(), isNull(),
                eq("DOLAR"), eq(7000.0), any(OutputStream.class));
    }

    @Test
    void balanceUsaElBalanceDeLaCaja() throws Exception {
        PdvCaja caja = new PdvCaja();
        caja.setId(662L);
        PdvCajaBalanceDto balance = new PdvCajaBalanceDto();
        when(pdvCajaService.findById(662L)).thenReturn(Optional.of(caja));
        when(pdvCajaService.generarBalance(caja)).thenReturn(balance);
        doAnswer(escribeEnArgumento(3)).when(impresionService).printBalance(any(), any(), any(),
                any(OutputStream.class));

        assertEquals(TICKET_B64, resolver.ticketEscpos(TicketEscposTipo.BALANCE, 662L, null, "CAJA 1"));
        verify(impresionService).printBalance(same(balance), isNull(), eq("CAJA 1"), any(OutputStream.class));
    }

    @Test
    void gastoLlevaElFlagDeReimpresionYElLocal() throws Exception {
        Gasto gasto = new Gasto();
        gasto.setId(15L);
        gasto.setCaja(new PdvCaja());
        gasto.getCaja().setId(662L);
        gasto.setRetiroGs(50000.0);
        when(gastoService.findById(15L)).thenReturn(Optional.of(gasto));
        doAnswer(escribeEnArgumento(3)).when(impresionService).printGasto(any(), any(), any(),
                any(OutputStream.class));

        assertEquals(TICKET_B64, resolver.ticketEscpos(TicketEscposTipo.GASTO, 15L, false, "CAJA 1"));
        ArgumentCaptor<GastoDto> dto = ArgumentCaptor.forClass(GastoDto.class);
        verify(impresionService).printGasto(dto.capture(), isNull(), eq("CAJA 1"), any(OutputStream.class));
        assertEquals(Long.valueOf(15L), dto.getValue().getId());
        assertEquals(Long.valueOf(662L), dto.getValue().getCajaId());
        assertEquals(Double.valueOf(50000.0), dto.getValue().getRetiroGs());
        assertFalse(dto.getValue().getReimpresion());
    }

    @Test
    void retiroTomaLosMontosDeSusDetalles() throws Exception {
        Retiro retiro = new Retiro();
        retiro.setId(9L);
        retiro.setCajaSalida(new PdvCaja());
        retiro.getCajaSalida().setId(662L);
        when(retiroService.findById(9L)).thenReturn(Optional.of(retiro));
        when(retiroDetalleService.findByRetiroIdAndMonedaId(9L, 1L)).thenReturn(200000.0);
        doAnswer(escribeEnArgumento(4)).when(impresionService).printRetiro(any(), any(), any(), any(),
                any(OutputStream.class));

        assertEquals(TICKET_B64, resolver.ticketEscpos(TicketEscposTipo.RETIRO, 9L, true, null));
        ArgumentCaptor<RetiroDto> dto = ArgumentCaptor.forClass(RetiroDto.class);
        verify(impresionService).printRetiro(dto.capture(), isNull(), isNull(), eq(true), any(OutputStream.class));
        assertEquals(Double.valueOf(200000.0), dto.getValue().getRetiroGs());
    }

    @Test
    void deliveryReimprimeElTicketDeSuVenta() throws Exception {
        Delivery delivery = new Delivery();
        delivery.setId(33L);
        delivery.setSucursalId(1L);
        Venta venta = new Venta();
        venta.setId(80078L);
        when(deliveryService.findById(33L)).thenReturn(Optional.of(delivery));
        when(ventaRepository.findByDeliveryIdAndSucursalId(33L, 1L)).thenReturn(venta);
        doAnswer(escribeEnArgumento(10)).when(ventaGraphQL).printTicket58mm(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(OutputStream.class));

        assertEquals(TICKET_B64, resolver.ticketEscpos(TicketEscposTipo.DELIVERY, 33L, null, "CAJA 1"));
        verify(ventaGraphQL).printTicket58mm(eq(venta), isNull(), any(), isNull(), eq(true), isNull(),
                eq("CAJA 1"), eq(false), isNull(), eq(delivery), any(OutputStream.class));
    }

    @Test
    void senaCuponArmaElMismoDtoQueImprimirSenaCupon() throws Exception {
        SenaCuponInput input = new SenaCuponInput();
        input.setVentaId(80078L);
        input.setVentaTarjetaId(12L);
        input.setQr("FRC-SENA|80078|12");
        input.setMonto(150000.0);
        doAnswer(escribeEnArgumento(3)).when(impresionService).printSenaCupon(any(), any(), any(),
                any(OutputStream.class));

        assertEquals(TICKET_B64, resolver.senaCuponEscpos(input, "CAJA 1"));
        ArgumentCaptor<SenaCuponDto> dto = ArgumentCaptor.forClass(SenaCuponDto.class);
        verify(impresionService).printSenaCupon(dto.capture(), isNull(), eq("CAJA 1"), any(OutputStream.class));
        assertEquals("FRC-SENA|80078|12", dto.getValue().getQr());
        assertEquals(Long.valueOf(12L), dto.getValue().getVentaTarjetaId());
    }

    @Test
    void siNoExisteLoQueSePideFallaConMensaje() {
        when(gastoService.findById(99L)).thenReturn(Optional.empty());
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.ticketEscpos(TicketEscposTipo.GASTO, 99L, false, null));
        assertTrue(e.getMessage().contains("99"));
    }

    @Test
    void siElRendererNoEscribeNadaDevuelveNull() throws Exception {
        PdvCaja caja = new PdvCaja();
        when(pdvCajaService.findById(662L)).thenReturn(Optional.of(caja));
        when(pdvCajaService.generarBalance(caja)).thenReturn(new PdvCajaBalanceDto());
        assertNull(resolver.ticketEscpos(TicketEscposTipo.BALANCE, 662L, null, null));
    }
}
