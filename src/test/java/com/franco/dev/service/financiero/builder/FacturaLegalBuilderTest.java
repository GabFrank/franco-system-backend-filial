package com.franco.dev.service.financiero.builder;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.sifen.service.SifenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FacturaLegalBuilderTest {

    @Mock private FacturaLegalService facturaLegalService;
    @Mock private FacturaLegalItemService facturaLegalItemService;
    @Mock private TimbradoDetalleService timbradoDetalleService;
    @Mock private ProductoService productoService;
    @Mock private VentaItemService ventaItemService;
    @Mock private PresentacionService presentacionService;
    @Mock private UsuarioService usuarioService;
    @Mock private SifenService sifenService;

    private FacturaLegalBuilder builder;

    private TimbradoDetalle timbradoDetalle;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        builder = new FacturaLegalBuilder(
                facturaLegalService, facturaLegalItemService, timbradoDetalleService,
                productoService, ventaItemService, presentacionService, usuarioService, sifenService);

        Sucursal sucursal = new Sucursal();
        sucursal.setId(2L);

        Timbrado timbrado = new Timbrado();
        timbrado.setIsElectronico(false);

        timbradoDetalle = new TimbradoDetalle();
        timbradoDetalle.setId(9L);
        timbradoDetalle.setNumeroActual(5104L);
        timbradoDetalle.setSucursal(sucursal);
        timbradoDetalle.setTimbrado(timbrado);

        when(sifenService.isSifenEnabled()).thenReturn(false);
        when(timbradoDetalleService.getTimbradoDetalleActual(eq(3L), eq(false))).thenReturn(timbradoDetalle);
        when(facturaLegalService.save(any(FacturaLegal.class)))
                .thenAnswer(inv -> {
                    FacturaLegal fl = inv.getArgument(0);
                    if (fl.getId() == null) fl.setId(100L);
                    return fl;
                });
        when(facturaLegalItemService.save(any(FacturaLegalItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private FacturaLegalItemDescriptor descriptor(Long productoId, Integer iva, String desc, double total) {
        FacturaLegalItemDescriptor d = new FacturaLegalItemDescriptor();
        d.setProductoId(productoId);
        d.setIva(iva);
        d.setDescripcion(desc);
        d.setTotal(total);
        d.setPrecioUnitario(total);
        d.setCantidad(1.0f);
        return d;
    }

    private Producto producto(Long id, Integer iva) {
        Producto p = new Producto();
        p.setId(id);
        p.setIva(iva);
        return p;
    }

    @Test
    void creaFLConItemCatalogo_vinculaProductoYIva() throws Exception {
        Producto p = producto(9944L, 10);
        when(productoService.findById(9944L)).thenReturn(Optional.of(p));

        FacturaLegal fl = new FacturaLegal();
        fl.setRuc("X");
        fl.setNombre("CONSUMIDOR FINAL");
        fl.setDescuento(0.0);

        BuildRequest req = new BuildRequest(
                fl,
                Collections.singletonList(descriptor(9944L, null, "DURACELL", 11000.0)),
                3L, false);

        FacturaLegal result = builder.build(req);

        assertNotNull(result);
        verify(facturaLegalItemService, times(1)).save(any(FacturaLegalItem.class));
        // FL save: 1 inicial + 1 con parciales = 2
        verify(facturaLegalService, times(2)).save(any(FacturaLegal.class));
        // sifen no llamado (timbrado no electronico)
        verify(sifenService, never()).crearDocumentoElectronico(any());

        // parciales
        assertEquals(11000.0, result.getTotalParcial10(), 0.01);
        assertEquals(1000.0, result.getIvaParcial10(), 0.01);
        assertEquals(0.0, result.getTotalParcial0(), 0.01);
        assertEquals(11000.0, result.getTotalFinal(), 0.01);
    }

    @Test
    void creaFLConDescuento_distribuyeProporcional() {
        Producto p10 = producto(1L, 10);
        Producto p0 = producto(2L, 0);
        when(productoService.findById(1L)).thenReturn(Optional.of(p10));
        when(productoService.findById(2L)).thenReturn(Optional.of(p0));

        FacturaLegal fl = new FacturaLegal();
        fl.setRuc("X");
        fl.setDescuento(2100.0); // 10% sobre 21000

        BuildRequest req = new BuildRequest(
                fl,
                Arrays.asList(
                        descriptor(1L, null, "ITEM10", 11000.0),
                        descriptor(2L, null, "ITEM0", 10000.0)
                ),
                3L, false);

        FacturaLegal result = builder.build(req);
        // Bruto: 11000 p10 + 10000 p0 = 21000, descuento 2100 (10%)
        // Neto: p10 = 9900, p0 = 9000, total = 18900
        assertEquals(9000.0, result.getTotalParcial0(), 0.01);
        assertEquals(9900.0, result.getTotalParcial10(), 0.01);
        assertEquals(18900.0, result.getTotalFinal(), 0.01);
        assertEquals(900.0, result.getIvaParcial10(), 0.01);
    }

    @Test
    void itemSinProducto_resolvedDescripcion() {
        Producto match = producto(99L, 5);
        when(productoService.findByDescripcionNormalized("TESTENAT")).thenReturn(Collections.singletonList(match));

        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        BuildRequest req = new BuildRequest(
                fl,
                Collections.singletonList(descriptor(null, null, "TESTENAT", 21000.0)),
                3L, false);

        FacturaLegal result = builder.build(req);
        assertEquals(21000.0, result.getTotalParcial5(), 0.01);
        assertEquals(1000.0, result.getIvaParcial5(), 0.01);
        assertNull(result.getCdc()); // sin sifen
    }

    @Test
    void timbradoElectronico_llamaSifen_actualizaCDC() throws Exception {
        timbradoDetalle.getTimbrado().setIsElectronico(true);
        when(sifenService.isSifenEnabled()).thenReturn(true);
        when(timbradoDetalleService.getTimbradoDetalleActual(eq(3L), eq(true))).thenReturn(timbradoDetalle);

        DocumentoElectronico de = new DocumentoElectronico();
        de.setCdc("01800000000000000000000000000000000000000000");
        when(sifenService.crearDocumentoElectronico(any(FacturaLegal.class))).thenReturn(de);

        Producto p = producto(1L, 10);
        when(productoService.findById(1L)).thenReturn(Optional.of(p));

        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        BuildRequest req = new BuildRequest(
                fl,
                Collections.singletonList(descriptor(1L, null, "ITEM", 11000.0)),
                3L, true);

        FacturaLegal result = builder.build(req);
        assertEquals("01800000000000000000000000000000000000000000", result.getCdc());
        verify(sifenService, times(1)).crearDocumentoElectronico(any());
    }

    @Test
    void sifenFalla_facturaPersisteSinCDC() throws Exception {
        timbradoDetalle.getTimbrado().setIsElectronico(true);
        when(sifenService.isSifenEnabled()).thenReturn(true);
        when(timbradoDetalleService.getTimbradoDetalleActual(eq(3L), eq(true))).thenReturn(timbradoDetalle);

        when(sifenService.crearDocumentoElectronico(any(FacturaLegal.class)))
                .thenThrow(new RuntimeException("SIFEN unavailable"));

        Producto p = producto(1L, 10);
        when(productoService.findById(1L)).thenReturn(Optional.of(p));

        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        BuildRequest req = new BuildRequest(
                fl,
                Collections.singletonList(descriptor(1L, null, "ITEM", 11000.0)),
                3L, true);

        FacturaLegal result = builder.build(req);
        // No lanza excepcion, factura persiste sin CDC
        assertNotNull(result);
        assertNull(result.getCdc());
        assertEquals(11000.0, result.getTotalFinal(), 0.01);
    }
}
