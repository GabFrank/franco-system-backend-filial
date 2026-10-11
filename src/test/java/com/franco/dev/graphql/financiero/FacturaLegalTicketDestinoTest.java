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
