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
import com.franco.dev.service.financiero.PoliticaFacturacionService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.sifen.service.SifenService;
import com.franco.dev.service.sifen.util.SifenReceptorHelper;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockitoAnnotations;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
        timbradoDetalle.setTelefono("021123456");

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

    // ---- activo en el alta (issue #131) ----

    /** Valor de activo en cada llamada a save, en orden: el objeto es mutable y un captor veria solo el final. */
    private List<Boolean> activoEnCadaSave() {
        List<Boolean> vistos = new ArrayList<>();
        when(facturaLegalService.save(any(FacturaLegal.class)))
                .thenAnswer(inv -> {
                    FacturaLegal fl = inv.getArgument(0);
                    vistos.add(fl.getActivo());
                    if (fl.getId() == null) fl.setId(100L);
                    return fl;
                });
        return vistos;
    }

    private BuildRequest requestDeUnItem(FacturaLegal fl, boolean electronica) {
        when(productoService.findById(1L)).thenReturn(Optional.of(producto(1L, 10)));
        return new BuildRequest(fl, Collections.singletonList(descriptor(1L, null, "ITEM", 11000.0)), 3L, electronica);
    }

    @Test
    void facturaPapelConActivoNull_naceActiva() {
        List<Boolean> vistos = activoEnCadaSave();
        FacturaLegal fl = new FacturaLegal();
        // null explicito: sin esto el default del entity taparia la falta de la guarda del builder
        fl.setActivo(null);
        fl.setDescuento(0.0);

        FacturaLegal result = builder.build(requestDeUnItem(fl, false));

        assertEquals(Boolean.TRUE, vistos.get(0), "el primer save es el INSERT: ya tiene que ir en true");
        assertEquals(Boolean.TRUE, result.getActivo());
    }

    @Test
    void facturaElectronicaConActivoNull_naceActiva() throws Exception {
        timbradoDetalle.getTimbrado().setIsElectronico(true);
        when(sifenService.isSifenEnabled()).thenReturn(true);
        when(timbradoDetalleService.getTimbradoDetalleActual(eq(3L), eq(true))).thenReturn(timbradoDetalle);
        DocumentoElectronico de = new DocumentoElectronico();
        de.setCdc("01800000000000000000000000000000000000000000");
        when(sifenService.crearDocumentoElectronico(any(FacturaLegal.class))).thenReturn(de);

        List<Boolean> vistos = activoEnCadaSave();
        FacturaLegal fl = new FacturaLegal();
        fl.setActivo(null);
        fl.setDescuento(0.0);

        FacturaLegal result = builder.build(requestDeUnItem(fl, true));

        assertEquals(Boolean.TRUE, vistos.get(0));
        assertEquals(Boolean.TRUE, result.getActivo());
        for (Boolean visto : vistos) {
            assertEquals(Boolean.TRUE, visto, "ningun save posterior lo vuelve a null");
        }
    }

    @Test
    void facturaConActivoFalse_seRespeta() {
        List<Boolean> vistos = activoEnCadaSave();
        FacturaLegal fl = new FacturaLegal();
        fl.setActivo(false);
        fl.setDescuento(0.0);

        FacturaLegal result = builder.build(requestDeUnItem(fl, false));

        assertEquals(Boolean.FALSE, vistos.get(0));
        assertEquals(Boolean.FALSE, result.getActivo());
    }

    @Test
    void facturaNueva_naceActivaPorElDefaultDelEntity() {
        assertEquals(Boolean.TRUE, new FacturaLegal().getActivo());
    }

    // ---- telefono del emisor (issue #139) ----

    private void timbradoElectronicoConSifen(String telefonoDetalle, String telefonoCabecera) throws Exception {
        timbradoDetalle.getTimbrado().setIsElectronico(true);
        timbradoDetalle.getTimbrado().setTelefono(telefonoCabecera);
        timbradoDetalle.setTelefono(telefonoDetalle);
        when(sifenService.isSifenEnabled()).thenReturn(true);
        when(timbradoDetalleService.getTimbradoDetalleActual(eq(3L), eq(true))).thenReturn(timbradoDetalle);
        DocumentoElectronico de = new DocumentoElectronico();
        de.setCdc("01800000000000000000000000000000000000000000");
        when(sifenService.crearDocumentoElectronico(any(FacturaLegal.class))).thenReturn(de);
    }

    private void verificarQueNoSeEscribioNada() throws Exception {
        verify(facturaLegalService, never()).save(any(FacturaLegal.class));
        verify(facturaLegalItemService, never()).save(any(FacturaLegalItem.class));
        verify(timbradoDetalleService, never()).save(any(TimbradoDetalle.class));
        verify(sifenService, never()).crearDocumentoElectronico(any(FacturaLegal.class));
        assertEquals(Long.valueOf(5104L), timbradoDetalle.getNumeroActual(), "no se consume numero de timbrado");
    }

    @Test
    void electronicaSinTelefonoEnNingunLado_cortaAntesDeEscribir() throws Exception {
        timbradoElectronicoConSifen("  ", null);
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> builder.build(requestDeUnItem(fl, true)));

        assertTrue(e.getMessage().startsWith("TIMBRADO_SIN_TELEFONO"), e.getMessage());
        verificarQueNoSeEscribioNada();
    }

    /** La API arma el request con el detalle resuelto y generarDE=false: crea el DE por su cuenta. */
    @Test
    void electronicaSinTelefonoPorLaApi_cortaAntesDeEscribir() throws Exception {
        timbradoElectronicoConSifen(null, "");
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);
        when(productoService.findById(1L)).thenReturn(Optional.of(producto(1L, 10)));
        BuildRequest req = new BuildRequest(fl,
                Collections.singletonList(descriptor(1L, null, "ITEM", 11000.0)), timbradoDetalle, false);

        GraphQLException e = assertThrows(GraphQLException.class, () -> builder.build(req));

        assertTrue(e.getMessage().startsWith("TIMBRADO_SIN_TELEFONO"), e.getMessage());

        verificarQueNoSeEscribioNada();
    }

    @Test
    void electronicaConTelefonoSoloEnLaCabecera_seEmite() throws Exception {
        timbradoElectronicoConSifen(null, "0982700027");
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        FacturaLegal result = builder.build(requestDeUnItem(fl, true));

        assertEquals("01800000000000000000000000000000000000000000", result.getCdc());
    }

    @Test
    void papelSinTelefono_seEmite() {
        timbradoDetalle.setTelefono(null);
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        FacturaLegal result = builder.build(requestDeUnItem(fl, false));

        assertNotNull(result);
        assertEquals(Long.valueOf(5105L), timbradoDetalle.getNumeroActual());
    }

    // ---- ensayo del DE antes de escribir (issue #170) ----

    @Test
    void elEnsayoDelDEFalla_cortaAntesDeEscribirYSinCausa() throws Exception {
        timbradoElectronicoConSifen("021123456", null);
        doThrow(new IllegalArgumentException("El precio unitario del item 'REGALO' debe ser mayor a cero"))
                .when(sifenService).validarGeneracionDE(any(FacturaLegal.class), any());
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> builder.build(requestDeUnItem(fl, true)));

        assertTrue(e.getMessage().startsWith("FACTURA_NO_EMITIBLE"), e.getMessage());
        assertTrue(e.getMessage().contains("REGALO"), e.getMessage());
        // Con causa encadenada el turno del contador no volveria: se decide por la causa raiz,
        // tambien cuando saveFacturaLegal la re-envuelve.
        assertNull(e.getCause());
        assertTrue(PoliticaFacturacionService.fallaAntesDeEscribir(e));
        assertTrue(PoliticaFacturacionService.fallaAntesDeEscribir(new GraphQLException("envuelta", e)));
        verificarQueNoSeEscribioNada();
    }

    @Test
    void elEnsayoVaAntesDelPrimerSaveYVeElTotalCalculado() throws Exception {
        timbradoElectronicoConSifen("021123456", null);
        List<Double> totalEnElEnsayo = new ArrayList<>();
        List<Integer> itemsEnElEnsayo = new ArrayList<>();
        doAnswer(inv -> {
            FacturaLegal f = inv.getArgument(0);
            List<FacturaLegalItem> its = inv.getArgument(1);
            totalEnElEnsayo.add(f.getTotalFinal());
            itemsEnElEnsayo.add(its.size());
            assertEquals(Integer.valueOf(5105), f.getNumeroFactura());
            return null;
        }).when(sifenService).validarGeneracionDE(any(FacturaLegal.class), any());
        List<Double> totalEnCadaSave = new ArrayList<>();
        when(facturaLegalService.save(any(FacturaLegal.class))).thenAnswer(inv -> {
            FacturaLegal f = inv.getArgument(0);
            totalEnCadaSave.add(f.getTotalFinal());
            if (f.getId() == null) f.setId(100L);
            return f;
        });
        // Como llega desde la venta: sin totalFinal.
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(1000.0);

        FacturaLegal result = builder.build(requestDeUnItem(fl, true));

        assertEquals(Collections.singletonList(10000.0), totalEnElEnsayo);
        assertEquals(Collections.singletonList(1), itemsEnElEnsayo);
        assertNull(totalEnCadaSave.get(0), "el INSERT de la cabecera sale como siempre, sin el total");
        assertEquals(10000.0, result.getTotalFinal(), 0.01);
        InOrder orden = inOrder(sifenService, facturaLegalService, facturaLegalItemService, timbradoDetalleService);
        orden.verify(sifenService).validarGeneracionDE(any(FacturaLegal.class), any());
        orden.verify(facturaLegalService).save(any(FacturaLegal.class));
        orden.verify(facturaLegalItemService).save(any(FacturaLegalItem.class));
        orden.verify(timbradoDetalleService).save(any(TimbradoDetalle.class));
        orden.verify(facturaLegalService).save(any(FacturaLegal.class));
    }

    /** La API manda generarDE=false y crea el DE por su cuenta: tambien se ensaya. */
    @Test
    void porLaApiTambienSeEnsaya() throws Exception {
        timbradoElectronicoConSifen("021123456", null);
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);
        when(productoService.findById(1L)).thenReturn(Optional.of(producto(1L, 10)));

        builder.build(new BuildRequest(fl,
                Collections.singletonList(descriptor(1L, null, "ITEM", 11000.0)), timbradoDetalle, false));

        verify(sifenService, times(1)).validarGeneracionDE(any(FacturaLegal.class), any());
        verify(sifenService, never()).crearDocumentoElectronico(any(FacturaLegal.class));
    }

    @Test
    void enPapelNoSeEnsaya() throws Exception {
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        builder.build(requestDeUnItem(fl, false));

        verify(sifenService, never()).validarGeneracionDE(any(FacturaLegal.class), any());
    }

    @Test
    void conElInterruptorApagadoNoSeEnsaya() throws Exception {
        timbradoElectronicoConSifen("021123456", null);
        builder.setEnsayoDeHabilitado("false");
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        FacturaLegal result = builder.build(requestDeUnItem(fl, true));

        verify(sifenService, never()).validarGeneracionDE(any(FacturaLegal.class), any());
        assertEquals("01800000000000000000000000000000000000000000", result.getCdc());
    }

    /** Un valor mal escrito en la configuracion no apaga el ensayo (ni tumba el arranque). */
    @Test
    void soloFalseApagaElEnsayo() throws Exception {
        timbradoElectronicoConSifen("021123456", null);
        for (String valor : new String[]{"true", "", "  ", "si", "flase", null}) {
            builder.setEnsayoDeHabilitado(valor);
            FacturaLegal fl = new FacturaLegal();
            fl.setDescuento(0.0);
            builder.build(requestDeUnItem(fl, true));
        }
        verify(sifenService, times(6)).validarGeneracionDE(any(FacturaLegal.class), any());

        builder.setEnsayoDeHabilitado(" FALSE ");
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);
        builder.build(requestDeUnItem(fl, true));
        verify(sifenService, times(6)).validarGeneracionDE(any(FacturaLegal.class), any());
    }

    /** Un NullPointerException del armado no tiene mensaje: el motivo no puede salir como "null". */
    @Test
    void elEnsayoFallaSinMensaje_elMotivoEsElTipoDeError() throws Exception {
        timbradoElectronicoConSifen("021123456", null);
        doThrow(new NullPointerException()).when(sifenService).validarGeneracionDE(any(FacturaLegal.class), any());
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> builder.build(requestDeUnItem(fl, true)));

        assertEquals("FACTURA_NO_EMITIBLE: NullPointerException", e.getMessage());
    }

    /** Lo que el cajero puede corregir rechaza la venta, en vez de dejarla sin factura. */
    @Test
    void innominadaSobreElTope_rechazaLaVentaSinEscribir() throws Exception {
        timbradoElectronicoConSifen("021123456", null);
        doThrow(new SifenReceptorHelper.FacturaInnominadaSobreTopeException("Factura innominada no permitida"))
                .when(sifenService).validarGeneracionDE(any(FacturaLegal.class), any());
        FacturaLegal fl = new FacturaLegal();
        fl.setDescuento(0.0);

        VentaNoFacturableException e = assertThrows(VentaNoFacturableException.class,
                () -> builder.build(requestDeUnItem(fl, true)));

        assertTrue(e.getMessage().contains("Cargue un cliente con nombre y documento"), e.getMessage());
        assertNull(e.getCause());
        assertTrue(PoliticaFacturacionService.fallaAntesDeEscribir(e));
        verificarQueNoSeEscribioNada();
    }

    @Test
    void ventaNoFacturable_seEncuentraAunqueVengaEnvuelta() {
        VentaNoFacturableException rechazo = new VentaNoFacturableException("cargue el cliente");

        assertEquals(rechazo, VentaNoFacturableException.en(rechazo));
        assertEquals(rechazo, VentaNoFacturableException.en(
                new GraphQLException("Error al guardar factura legal: cargue el cliente", rechazo)));
        assertNull(VentaNoFacturableException.en(new GraphQLException("FACTURA_NO_EMITIBLE: ...")));
        assertNull(VentaNoFacturableException.en(null));
    }
}
