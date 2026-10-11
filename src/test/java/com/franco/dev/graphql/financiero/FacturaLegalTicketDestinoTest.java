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

    /** Venta a credito facturada (issue #133): un solo papel, la factura completa y el pagare al pie. */
    @Test
    void facturaDeVentaACreditoLlevaElPagareAlPie() throws Exception {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        facturaLegalGraphQL.printTicket58mmFactura(ventaACredito(), factura, items, null, memoria,
                java.util.Arrays.asList(cuota(9000.0, "2026-10-29 10:30"), cuota(9000.0, "2026-11-29 10:30")),
                "CAJA 1");

        String texto = new String(memoria.toByteArray(), StandardCharsets.ISO_8859_1);
        int pie = texto.indexOf("GRACIAS POR LA PREFERENCIA");
        int pagare = texto.indexOf("PAGARE A LA ORDEN 1/2");
        assertTrue(pie > 0, "pie de la factura");
        assertTrue(pagare > pie, "el pagare va despues de la factura");
        assertTrue(texto.contains("PAGARE A LA ORDEN 2/2"), "segundo pagare");
        assertTrue(texto.contains("El dia 29-11-2026"), "vencimiento de la segunda cuota");
        assertTrue(texto.contains("Local: CAJA 1"), "caja");
        assertTrue(texto.contains("Deudor: CLIENTE A CREDITO"), "deudor");
    }

    /** Si el pagare no se puede armar (cuota sin vencimiento), la factura sale completa igual. */
    @Test
    void siElPagareFallaLaFacturaSaleIgual() throws Exception {
        ByteArrayOutputStream sinPagare = new ByteArrayOutputStream();
        facturaLegalGraphQL.printTicket58mmFactura(ventaACredito(), factura, items, null, sinPagare);

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        facturaLegalGraphQL.printTicket58mmFactura(ventaACredito(), factura, items, null, memoria,
                Collections.singletonList(cuota(9000.0, null)), "CAJA 1");

        byte[] factura = sinPagare.toByteArray();
        byte[] conPagareRoto = memoria.toByteArray();
        // La factura sin pagare termina en "GRACIAS..." + el avance final de papel.
        int pie = new String(factura, StandardCharsets.ISO_8859_1).lastIndexOf("GRACIAS POR LA PREFERENCIA");
        byte[] cierre = java.util.Arrays.copyOfRange(factura, pie + "GRACIAS POR LA PREFERENCIA".length(),
                factura.length);
        assertArrayEquals(java.util.Arrays.copyOfRange(factura, 0, pie),
                java.util.Arrays.copyOfRange(conPagareRoto, 0, pie), "la factura sale entera");
        assertTrue(conPagareRoto.length > factura.length, "el pagare se llego a intentar");
        // Despues del pagare fallido el ticket se cierra con el mismo avance final.
        byte[] avance = java.util.Arrays.copyOfRange(cierre, cierre.length - 3, cierre.length);
        assertArrayEquals(avance, java.util.Arrays.copyOfRange(conPagareRoto, conPagareRoto.length - 3,
                conPagareRoto.length), "el ticket se cierra");
    }

    private static com.franco.dev.domain.operaciones.Venta ventaACredito() {
        com.franco.dev.domain.personas.Persona persona = new com.franco.dev.domain.personas.Persona();
        persona.setNombre("Cliente a credito");
        persona.setDocumento("1234567");
        com.franco.dev.domain.personas.Cliente cliente = new com.franco.dev.domain.personas.Cliente();
        cliente.setPersona(persona);
        com.franco.dev.domain.operaciones.Venta venta = new com.franco.dev.domain.operaciones.Venta();
        venta.setId(80078L);
        venta.setCliente(cliente);
        venta.setCreadoEn(LocalDateTime.of(2026, 9, 29, 10, 30));
        return venta;
    }

    private static com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput cuota(Double valor,
            String vencimiento) {
        com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput c =
                new com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput();
        c.setValor(valor);
        c.setVencimiento(vencimiento);
        return c;
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
}
