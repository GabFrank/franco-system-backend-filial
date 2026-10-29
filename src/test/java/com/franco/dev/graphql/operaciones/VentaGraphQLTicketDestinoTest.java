package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.AjusteCobro;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.LoteTicketService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.output.CapturaPrintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * El ticket de venta (y el pagare) escrito en memoria para imprimir desde el cliente tiene que ser,
 * byte por byte, el mismo que hoy le llega a la impresora del servidor, sin buscar impresora.
 */
class VentaGraphQLTicketDestinoTest {

    @Mock private PrintingService printingService;
    @Mock private ImageService imageService;
    @Mock private SucursalService sucursalService;
    @Mock private CambioService cambioService;
    @Mock private MonedaService monedaService;
    @Mock private CobroDetalleService cobroDetalleService;
    @Mock private VentaItemService ventaItemService;
    @Mock private LoteTicketService loteTicketService;
    @Mock private com.franco.dev.service.financiero.FacturaService facturaService;
    @Mock private com.franco.dev.service.financiero.PoliticaFacturacionService politicaFacturacionService;
    @InjectMocks private VentaGraphQL ventaGraphQL;

    private CapturaPrintService impresora;
    private Venta venta;
    private Cobro cobro;
    private List<VentaItem> items;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        File dir = Files.createTempDirectory("frc-ticket").toFile();
        dir.deleteOnExit();
        imageService.storageDirectoryPath = CapturaPrintService.directorioConLogo(dir);

        Ciudad ciudad = new Ciudad();
        ciudad.setDescripcion("SALTO DEL GUAIRA");
        Sucursal sucursal = new Sucursal();
        sucursal.setNombre("CENTRO");
        sucursal.setCiudad(ciudad);
        sucursal.setNroDelivery("595981000000");
        when(sucursalService.sucursalActual()).thenReturn(sucursal);

        when(cambioService.findLastValorEnGsByMonedaIdOrDefault(anyLong(), anyDouble())).thenReturn(7000.0);
        Moneda gs = moneda(1L, "GUARANI");
        when(monedaService.findById(anyLong())).thenReturn(Optional.of(gs));
        when(cobroDetalleService.ajusteDe(any(), any())).thenReturn(AjusteCobro.deDetalles(Collections.emptyList()));

        FormaPago efectivo = new FormaPago();
        efectivo.setId(1L);
        efectivo.setDescripcion("EFECTIVO");
        CobroDetalle pago = new CobroDetalle();
        pago.setPago(true);
        pago.setValor(20000.0);
        pago.setCambio(1.0);
        pago.setMoneda(gs);
        pago.setFormaPago(efectivo);
        when(cobroDetalleService.findByCobroId(anyLong())).thenReturn(Collections.singletonList(pago));

        impresora = new CapturaPrintService();
        when(printingService.getPrintService(any())).thenReturn(impresora);

        Persona cajero = new Persona();
        cajero.setNombre("CAJERO DE PRUEBA");
        Usuario usuario = new Usuario();
        usuario.setPersona(cajero);
        Persona cp = new Persona();
        cp.setNombre("CLIENTE");
        cp.setDocumento("1234567");
        Cliente cliente = new Cliente();
        cliente.setPersona(cp);

        cobro = new Cobro();
        cobro.setId(501L);
        venta = new Venta();
        venta.setId(80078L);
        venta.setUsuario(usuario);
        venta.setCliente(cliente);
        venta.setCobro(cobro);
        venta.setCreadoEn(LocalDateTime.of(2026, 9, 29, 10, 30));
        venta.setTotalGs(18000.0);
        venta.setTotalRs(0.0);
        venta.setTotalDs(0.0);
        items = Arrays.asList(item(1L, 2.0, 5000.0), item(2L, 1.0, 8000.0));
    }

    private static Moneda moneda(long id, String abreviatura) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(abreviatura);
        return m;
    }

    private static VentaItem item(long id, double cantidad, double precio) {
        Producto producto = new Producto();
        producto.setDescripcion("PRODUCTO " + id);
        Presentacion presentacion = new Presentacion();
        presentacion.setId(id);
        presentacion.setCantidad(1.0);
        presentacion.setProducto(producto);
        PrecioPorSucursal precioVenta = new PrecioPorSucursal();
        precioVenta.setPrecio(precio);
        VentaItem vi = new VentaItem();
        vi.setId(id);
        vi.setProducto(producto);
        vi.setPresentacion(presentacion);
        vi.setCantidad(cantidad);
        vi.setPrecio(precio);
        vi.setPrecioVenta(precioVenta);
        vi.setValorDescuento(0.0);
        return vi;
    }

    @Test
    void ticketEnMemoriaIgualAlImpreso() throws Exception {
        assertTrue(ventaGraphQL.printTicket58mm(venta, cobro, items, null, false, "ticket", "CAJA 1", false,
                null, null));
        byte[] impreso = impresora.bytes();

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        reset(printingService);
        assertTrue(ventaGraphQL.printTicket58mm(venta, cobro, items, null, false, "ticket", "CAJA 1", false,
                null, null, memoria));

        assertTrue(impreso.length > 0);
        assertArrayEquals(impreso, memoria.toByteArray());
        verifyNoInteractions(printingService);
    }

    @Test
    void reimpresionYPagareEnMemoriaIgualAlImpreso() throws Exception {
        assertTrue(ventaGraphQL.printTicket58mm(venta, cobro, items, null, true, "ticket", null, true,
                Collections.emptyList(), null));
        byte[] impreso = impresora.bytes();

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        reset(printingService);
        assertTrue(ventaGraphQL.printTicket58mm(venta, cobro, items, null, true, "ticket", null, true,
                Collections.emptyList(), null, memoria));

        assertTrue(impreso.length > 0);
        assertArrayEquals(impreso, memoria.toByteArray());
        verifyNoInteractions(printingService);
    }

    /** El ticket de una venta a credito lleva un pagare por cuota, numerado y con su vencimiento. */
    @Test
    void ticketConPagareLlevaUnPagarePorCuota() throws Exception {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        assertTrue(ventaGraphQL.printTicket58mm(venta, cobro, items, null, false, null, "CAJA 1", true,
                Arrays.asList(cuota(9000.0, "2026-10-29 10:30"), cuota(9000.0, "2026-11-29 10:30")), null,
                memoria));

        String texto = new String(memoria.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(texto.contains("PAGARE A LA ORDEN 1/2"), "primer pagare");
        assertTrue(texto.contains("PAGARE A LA ORDEN 2/2"), "segundo pagare");
        assertTrue(texto.contains("El dia 29-11-2026"), "vencimiento de la segunda cuota");
        assertTrue(texto.contains("Deudor: CLIENTE"), "deudor");
    }

    /** El pagare suelto de una venta facturada: solo los pagares, sin repetir la venta, y cortado. */
    @Test
    void pagareSueltoLlevaSoloLosPagares() throws Exception {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        ventaGraphQL.imprimirPagareSuelto(venta, null, "CAJA 1",
                Arrays.asList(cuota(9000.0, "2026-10-29 10:30"), cuota(9000.0, "2026-11-29 10:30")), memoria);

        byte[] bytes = memoria.toByteArray();
        String texto = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(texto.contains("PAGARE A LA ORDEN 1/2"), "primer pagare");
        assertTrue(texto.contains("PAGARE A LA ORDEN 2/2"), "segundo pagare");
        assertTrue(texto.contains("Venta: 80078"), "venta");
        assertTrue(texto.contains("Local: CAJA 1"), "caja");
        assertTrue(texto.contains("Deudor: CLIENTE"), "deudor");
        assertFalse(texto.contains("PRODUCTO 1"), "no repite los items");
        assertFalse(texto.contains("GRACIAS POR LA PREFERENCIA"), "no es el ticket de venta");
        // Termina cortando (GS V 0): el ticket-factura que sale despues queda en otro papel.
        byte[] corte = {0x1d, 'V', 48};
        assertArrayEquals(corte, Arrays.copyOfRange(bytes, bytes.length - 3, bytes.length), "corte final");
        assertEquals(1, texto.split(new String(corte, java.nio.charset.StandardCharsets.ISO_8859_1), -1).length - 1,
                "un solo corte");
    }

    @Test
    void pagareSueltoEnMemoriaIgualAlImpreso() throws Exception {
        List<com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput> cuotas =
                Collections.singletonList(cuota(18000.0, "2026-10-29 10:30"));
        ventaGraphQL.imprimirPagareSuelto(venta, "ticket", "CAJA 1", cuotas, null);
        byte[] impreso = impresora.bytes();

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        ventaGraphQL.imprimirPagareSuelto(venta, null, "CAJA 1", cuotas, memoria);

        assertTrue(impreso.length > 0);
        assertArrayEquals(impreso, memoria.toByteArray());
    }

    @Test
    void pagareSueltoSinCuotasNoSaca() throws Exception {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();

        ventaGraphQL.imprimirPagareSuelto(venta, null, "CAJA 1", Collections.emptyList(), memoria);

        assertEquals(0, memoria.size());
    }

    /** Si el pagare no se puede armar (cuota sin vencimiento) no sale nada: ni medio pagare. */
    @Test
    void pagareSueltoQueFallaNoEscribeNada() {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();

        assertThrows(Exception.class, () -> ventaGraphQL.imprimirPagareSuelto(venta, null, "CAJA 1",
                Collections.singletonList(cuota(9000.0, null)), memoria));

        assertEquals(0, memoria.size());
    }

    private static com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput cuota(double valor,
            String vencimiento) {
        com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput c =
                new com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput();
        c.setValor(valor);
        c.setVencimiento(vencimiento);
        return c;
    }

    @Test
    void sinImpresoraElServidorNoImprimeYElClienteIgualRecibeElTicket() throws Exception {
        when(printingService.getPrintService(any())).thenReturn(null);
        assertNull(ventaGraphQL.printTicket58mm(venta, cobro, items, null, false, "no-existe", null, false,
                null, null));

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        assertTrue(ventaGraphQL.printTicket58mm(venta, cobro, items, null, false, null, null, false, null, null,
                memoria));
        assertTrue(memoria.size() > 0);
    }

    /**
     * "Venta + Ticket" sin factura (issue #169): el respaldo es este mismo ticket, con lo que la
     * ruta tiene a mano. Sin stub de impresion: tiene que salir un ticket de verdad.
     */
    @Test
    void ventaMasTicketSinFacturaEntregaElTicketSimpleAlCliente() throws Exception {
        when(facturaService.crearFacturaLegalDesdeVenta(any(), any(), any(), any())).thenReturn(null);
        ByteArrayOutputStream esperado = new ByteArrayOutputStream();
        assertTrue(ventaGraphQL.printTicket58mm(venta, cobro, items, null, false, null, "CAJA 1", false, null,
                null, esperado));

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        ventaGraphQL.facturarEImprimir(venta, cobro, items, null, null, "CAJA 1", 3L, null, null,
                com.franco.dev.service.financiero.PoliticaFacturacion.sinFacturacionAutomatica(), memoria);

        assertTrue(memoria.size() > 0);
        assertArrayEquals(esperado.toByteArray(), memoria.toByteArray());
    }

    // ── Tabla de totales: el ajuste del cobro (descuento o aumento) en la columna del medio ──────────
    // Las lineas "Gs." de Rs y Us salen asi porque el mock de MonedaService devuelve GUARANI para las
    // tres monedas; lo que importa son los numeros (cambio 7000).

    private static final String ENCABEZADO_DESC = "      Parcial    Desc.     Final";
    private static final String ENCABEZADO_AUM = "      Parcial     Aum.     Final";

    private List<String> totalesCon(CobroDetalle... lineas) throws Exception {
        when(cobroDetalleService.ajusteDe(any(), any())).thenReturn(AjusteCobro.deDetalles(Arrays.asList(lineas)));
        return totalesDelTicket(null);
    }

    private List<String> totalesDelTicket(com.franco.dev.domain.operaciones.Delivery delivery) throws Exception {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        assertTrue(ventaGraphQL.printTicket58mm(venta, cobro, items, null, false, null, "CAJA 1", false, null,
                delivery, memoria));
        return com.franco.dev.utilitarios.print.TicketTexto.totales(memoria.toByteArray());
    }

    /** Fijado con el codigo anterior a este cambio: sin ajuste, la tabla no cambia. */
    @Test
    void totalesSinAjusteComoAntes() throws Exception {
        assertEquals(Arrays.asList(ENCABEZADO_DESC,
                "Gs.    18.000        0    18.000",
                "Gs.      2,57     0,00      2,57",
                "Gs.      2,57     0,00      2,57"), totalesDelTicket(null));
    }

    /** Fijado con el codigo anterior a este cambio: con descuento, la tabla no cambia. */
    @Test
    void totalesConDescuentoComoAntes() throws Exception {
        assertEquals(Arrays.asList(ENCABEZADO_DESC,
                "Gs.    18.000    3.000    15.000",
                "Gs.      2,57     0,43      2,14",
                "Gs.      2,57     0,43      2,14"), totalesCon(linea(false, true, 3000.0, 1.0)));
    }

    /** El aumento (cobrar de mas con F8) va en la columna del medio y suma al Final, que cierra con el pago. */
    @Test
    void totalesConAumento() throws Exception {
        assertEquals(Arrays.asList(ENCABEZADO_AUM,
                "Gs.    18.000    9.000    27.000",
                "Gs.      2,57     1,29      3,86",
                "Gs.      2,57     1,29      3,86"), totalesCon(linea(true, false, 9000.0, 1.0)));
    }

    /** Hay aumentos guardados en negativo (deliverys hasta 2024-10): se imprimen igual. */
    @Test
    void totalesConAumentoGuardadoEnNegativo() throws Exception {
        assertEquals(totalesCon(linea(true, false, 9000.0, 1.0)), totalesCon(linea(true, false, -9000.0, 1.0)));
    }

    /** Un aumento en reales da guaranies con decimales: se redondea, no se trunca (799 seria 1 Gs menos). */
    @Test
    void totalesConAumentoEnRealesSeRedondean() throws Exception {
        assertEquals("Gs.    18.000      800    18.800",
                totalesCon(linea(true, false, 0.695652173913043, 1150.0)).get(1));
    }

    /** Descuento y aumento en el mismo cobro (redondeo del PDV): se imprime el neto. */
    @Test
    void totalesConDescuentoYAumentoMuestranElNeto() throws Exception {
        List<String> totales = totalesCon(linea(false, true, 1380.0, 1.0), linea(true, false, 380.0, 1.0));
        assertEquals(ENCABEZADO_DESC, totales.get(0));
        assertEquals("Gs.    18.000    1.000    17.000", totales.get(1));
    }

    /** En un delivery el envio va en Parcial y el aumento aparte: no se suma dos veces. */
    @Test
    void totalesDeDeliveryConAumento() throws Exception {
        when(cobroDetalleService.ajusteDe(any(), any())).thenReturn(AjusteCobro.deDetalles(
                Collections.singletonList(linea(true, false, 9000.0, 1.0))));
        com.franco.dev.domain.operaciones.PrecioDelivery precio = new com.franco.dev.domain.operaciones.PrecioDelivery();
        precio.setValor(5000.0);
        com.franco.dev.domain.operaciones.Delivery delivery = new com.franco.dev.domain.operaciones.Delivery();
        delivery.setPrecio(precio);
        assertEquals(Arrays.asList(ENCABEZADO_AUM,
                "Gs.    23.000    9.000    32.000",
                "Gs.      3,29     1,29      4,57",
                "Gs.      3,29     1,29      4,57"), totalesDelTicket(delivery));
    }

    /**
     * Deliverys de 2022-2024 guardaron el aumento con vuelto = true tambien: con el aumento ya en los
     * totales, contarlo como vuelto en Detalles lo mostraria dos veces y el ticket no cerraria.
     */
    @Test
    void lineaDeAumentoMarcadaComoVueltoNoSeCuentaComoVuelto() throws Exception {
        Moneda gs = moneda(1L, "GUARANI");
        FormaPago efectivo = new FormaPago();
        efectivo.setId(1L);
        efectivo.setDescripcion("EFECTIVO");
        CobroDetalle pago = linea(false, false, 27000.0, 1.0);
        pago.setPago(true);
        pago.setMoneda(gs);
        pago.setFormaPago(efectivo);
        CobroDetalle aumentoYVuelto = linea(true, false, -9000.0, 1.0);
        aumentoYVuelto.setVuelto(true);
        aumentoYVuelto.setMoneda(gs);
        aumentoYVuelto.setFormaPago(efectivo);
        when(cobroDetalleService.findByCobroId(anyLong())).thenReturn(Arrays.asList(pago, aumentoYVuelto));
        when(cobroDetalleService.ajusteDe(any(), any())).thenReturn(AjusteCobro.deDetalles(Arrays.asList(pago, aumentoYVuelto)));

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        ventaGraphQL.printTicket58mm(venta, cobro, items, null, false, null, "CAJA 1", false, null, null, memoria);
        String texto = com.franco.dev.utilitarios.print.TicketTexto.plano(memoria.toByteArray());

        assertEquals("Gs.    18.000    9.000    27.000",
                com.franco.dev.utilitarios.print.TicketTexto.totales(memoria.toByteArray()).get(1));
        String detalles = texto.substring(texto.indexOf("------------Detalles------------"));
        String lineaPago = detalles.split("\n")[2];
        assertTrue(lineaPago.contains("27.000"), lineaPago);
        assertTrue(lineaPago.trim().endsWith(" 0"), "sin vuelto: " + lineaPago);
    }

    private static CobroDetalle linea(boolean aumento, boolean descuento, double valor, double cambio) {
        CobroDetalle cd = new CobroDetalle();
        cd.setAumento(aumento);
        cd.setDescuento(descuento);
        cd.setValor(valor);
        cd.setCambio(cambio);
        return cd;
    }
}
