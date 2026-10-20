package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.LoteTicketService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.utilitarios.print.output.CapturaPrintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * La factura en ticket escrita en memoria para imprimir desde el cliente: sale completa (con el QR
 * y el CDC de SIFEN) sin que haya ninguna impresora en el servidor.
 *
 * <p>El camino del servidor busca la impresora con un metodo estatico
 * ({@code PrinterOutputStream.getPrintServiceByName}) que no se puede sustituir aca; que los dos
 * caminos escriban lo mismo lo prueban los tickets de venta, gasto, retiro y balance, que llevan
 * el mismo cambio (ver VentaGraphQLTicketDestinoTest e ImpresionServiceDestinoTest).
 */
class FacturaLegalTicketDestinoTest {

    @Mock private ImageService imageService;
    @Mock private SucursalService sucursalService;
    @Mock private CambioService cambioService;
    @Mock private MonedaService monedaService;
    @Mock private CobroDetalleService cobroDetalleService;
    @Mock private FacturaLegalItemService facturaLegalItemService;
    @Mock private DocumentoElectronicoService documentoElectronicoService;
    @Mock private LoteTicketService loteTicketService;
    @Mock private ProductoService productoService;
    @InjectMocks private FacturaLegalGraphQL facturaLegalGraphQL;

    private FacturaLegal factura;
    private List<FacturaLegalItem> items;

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
        when(sucursalService.findById(anyLong())).thenReturn(Optional.of(sucursal));
        when(cambioService.findLastValorEnGsByMonedaIdOrDefault(anyLong(), anyDouble())).thenReturn(7000.0);
        Moneda gs = new Moneda();
        gs.setId(1L);
        gs.setDenominacion("GUARANI");
        when(monedaService.findById(anyLong())).thenReturn(Optional.of(gs));

        DocumentoElectronico de = new DocumentoElectronico();
        de.setCdc("01800000000001001000000122026092910000000001");
        de.setUrlQr("https://ekuatia.set.gov.py/consultas/qr?nVersion=150&Id=0180000000000100100000012202609291");
        when(documentoElectronicoService.findByFacturaLegalIdAndSucursalId(anyLong(), anyLong())).thenReturn(de);

        Timbrado timbrado = new Timbrado();
        timbrado.setRazonSocial("Franco Arevalos S.A.");
        timbrado.setRuc("80000000-1");
        timbrado.setNumero("12345678");
        timbrado.setIsElectronico(true);
        timbrado.setFechaInicio(LocalDateTime.of(2026, 1, 1, 0, 0));
        timbrado.setFechaFin(LocalDateTime.of(2027, 1, 1, 0, 0));
        TimbradoDetalle td = new TimbradoDetalle();
        td.setId(4L);
        td.setTimbrado(timbrado);
        td.setPuntoExpedicion("001");

        factura = new FacturaLegal();
        factura.setId(30044L);
        factura.setSucursalId(1L);
        factura.setTimbradoDetalle(td);
        factura.setNumeroFactura(122);
        factura.setCredito(false);
        factura.setNombre("CLIENTE DE PRUEBA");
        factura.setRuc("1234567");
        factura.setCreadoEn(LocalDateTime.of(2026, 9, 29, 10, 30));
        factura.setTotalFinal(18000.0);
        factura.setIvaParcial10(1636.0);
        factura.setIvaParcial5(0.0);
        factura.setTotalParcial0(0.0);
        factura.setDescuento(0.0);

        FacturaLegalItem item = new FacturaLegalItem();
        item.setDescripcion("producto de prueba");
        item.setCantidad(2.0f);
        item.setPrecioUnitario(9000.0);
        item.setTotal(18000.0);
        item.setIva(10);
        items = Collections.singletonList(item);
    }

    @Test
    void facturaElectronicaSeEscribeEnMemoriaSinImpresora() throws Exception {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        facturaLegalGraphQL.printTicket58mmFactura(null, factura, items, null, memoria);

        String texto = new String(memoria.toByteArray(), StandardCharsets.ISO_8859_1);
        assertTrue(texto.contains("FRANCO AREVALOS S.A."), "razon social");
        assertTrue(texto.contains("RUC: 80000000-1"), "ruc del timbrado");
        assertTrue(texto.contains("PRODUCTO DE PRUEBA"), "item");
        assertTrue(texto.contains("GRACIAS POR LA PREFERENCIA"), "pie");
    }

    @Test
    void facturaEnMonedaExtranjeraSeEscribeEnMemoriaSinImpresora() throws Exception {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        facturaLegalGraphQL.printTicket58mmFacturaMonedaExtranjera(null, factura, items, null, "DOLAR", 7000.0,
                memoria);

        String texto = new String(memoria.toByteArray(), StandardCharsets.ISO_8859_1);
        assertTrue(texto.contains("FRANCO AREVALOS S.A."), "razon social");
        assertTrue(texto.contains("GRACIAS POR LA PREFERENCIA"), "pie");
    }

    // ── Tabla de totales: el ajuste de la factura (descuento o aumento) en la columna del medio ──────

    private java.util.List<String> totalesDeLaFactura() throws Exception {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        facturaLegalGraphQL.printTicket58mmFactura(null, factura, items, null, memoria);
        return com.franco.dev.utilitarios.print.TicketTexto.totales(memoria.toByteArray());
    }

    /** Fijado con el codigo anterior a este cambio: sin ajuste, la tabla no cambia. */
    @Test
    void totalesSinAjusteComoAntes() throws Exception {
        assertEquals(java.util.Arrays.asList("      Parcial    Desc.     Final",
                "Gs.    18.000        0    18.000",
                "Gs.      2,57     0,00      2,57",
                "Gs.      2,57     0,00      2,57"), totalesDeLaFactura());
    }

    /** Fijado con el codigo anterior a este cambio: con descuento, la tabla no cambia. */
    @Test
    void totalesConDescuentoComoAntes() throws Exception {
        factura.setDescuento(3000.0);
        factura.setTotalFinal(15000.0);
        assertEquals(java.util.Arrays.asList("      Parcial    Desc.     Final",
                "Gs.    18.000    3.000    15.000",
                "Gs.      2,57     0,43      2,14",
                "Gs.      2,57     0,43      2,14"), totalesDeLaFactura());
    }

    /** La factura guarda el aumento como descuento negativo: antes salia "Desc. -9.000". */
    @Test
    void totalesConAumento() throws Exception {
        factura.setDescuento(-9000.0);
        factura.setTotalFinal(27000.0);
        assertEquals(java.util.Arrays.asList("      Parcial     Aum.     Final",
                "Gs.    18.000    9.000    27.000",
                "Gs.      2,57     1,29      3,86",
                "Gs.      2,57     1,29      3,86"), totalesDeLaFactura());
    }

    /** Un aumento en reales da guaranies con decimales: se redondea, igual que en el ticket simple. */
    @Test
    void totalesConAumentoConFraccionSeRedondean() throws Exception {
        factura.setDescuento(-799.9999999);
        factura.setTotalFinal(18800.0);
        assertEquals("Gs.    18.000      800    18.800", totalesDeLaFactura().get(1));
    }

    /** Factura sin descuento guardado: lo toma del cobro, y un aumento ahi tambien sale como Aum. */
    @Test
    void totalesConAumentoTomadoDelCobro() throws Exception {
        com.franco.dev.domain.operaciones.CobroDetalle aumento = new com.franco.dev.domain.operaciones.CobroDetalle();
        aumento.setAumento(true);
        aumento.setValor(9000.0);
        aumento.setCambio(1.0);
        when(cobroDetalleService.ajusteDe(any(), any())).thenReturn(
                com.franco.dev.service.operaciones.AjusteCobro.deDetalles(Collections.singletonList(aumento)));
        com.franco.dev.domain.operaciones.Venta venta = new com.franco.dev.domain.operaciones.Venta();
        venta.setCobro(new com.franco.dev.domain.operaciones.Cobro());
        factura.setTotalFinal(27000.0);

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        facturaLegalGraphQL.printTicket58mmFactura(venta, factura, items, null, memoria);
        java.util.List<String> totales = com.franco.dev.utilitarios.print.TicketTexto.totales(memoria.toByteArray());
        assertEquals("      Parcial     Aum.     Final", totales.get(0));
        assertEquals("Gs.    18.000    9.000    27.000", totales.get(1));
    }

    /** La factura en moneda extranjera usa la misma columna: Aum. y sin signo. */
    @Test
    void totalesEnMonedaExtranjeraConAumento() throws Exception {
        factura.setDescuento(-9000.0);
        factura.setTotalFinal(27000.0);
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        facturaLegalGraphQL.printTicket58mmFacturaMonedaExtranjera(null, factura, items, null, "DOLAR", 7000.0,
                memoria);
        java.util.List<String> totales = com.franco.dev.utilitarios.print.TicketTexto.totales(memoria.toByteArray());
        assertEquals("      Parcial     Aum.     Final", totales.get(0));
        assertTrue(totales.get(1).contains("1,29"), totales.get(1));
        assertFalse(totales.get(1).contains("-"), totales.get(1));
    }
}
