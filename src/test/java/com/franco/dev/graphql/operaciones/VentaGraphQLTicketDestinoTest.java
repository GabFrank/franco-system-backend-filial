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
        verifyZeroInteractions(printingService);
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
        verifyZeroInteractions(printingService);
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
}
