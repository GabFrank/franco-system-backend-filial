package com.franco.dev.service.financiero.builder;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.sifen.service.FacturaElectronicaDePrueba;
import com.franco.dev.service.sifen.service.SifenService;
import com.roshka.sifen.core.SifenConfig;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El ensayo del DE con el armado REAL (issue #170), con la factura tal como la arma cada llamador.
 * Es el test que detecta un falso positivo: el ensayo corre en todas las facturas, y si cortara una
 * valida, esa via dejaria de facturar en silencio. El caso mas fragil es el total: el armado lo
 * necesita y "Venta + Ticket" y delivery llegan sin el.
 */
class FacturaLegalBuilderConSifenRealTest {

    private static final long PDV = 3L;
    private static final String CDC = "01800000000000000000000000000000000000000000";

    @Mock private FacturaLegalService facturaLegalService;
    @Mock private FacturaLegalItemService facturaLegalItemService;
    @Mock private TimbradoDetalleService timbradoDetalleService;
    @Mock private ProductoService productoService;
    @Mock private VentaItemService ventaItemService;
    @Mock private PresentacionService presentacionService;
    @Mock private UsuarioService usuarioService;

    private SifenService sifenService;
    private FacturaLegalBuilder builder;
    private TimbradoDetalle timbradoDetalle;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // Habilitado, sin certificado ni red. Solo se reemplaza la creacion del DE, que persiste.
        sifenService = spy(new SifenService(null, null, null, null, null, null, null, null, new SifenConfig()));
        DocumentoElectronico de = new DocumentoElectronico();
        de.setCdc(CDC);
        doReturn(de).when(sifenService).crearDocumentoElectronico(any(FacturaLegal.class));

        builder = new FacturaLegalBuilder(facturaLegalService, facturaLegalItemService, timbradoDetalleService,
                productoService, ventaItemService, presentacionService, usuarioService, sifenService);

        timbradoDetalle = FacturaElectronicaDePrueba.timbradoDetalle();
        when(timbradoDetalleService.getTimbradoDetalleActual(eq(PDV), eq(true))).thenReturn(timbradoDetalle);
        when(facturaLegalService.save(any(FacturaLegal.class))).thenAnswer(inv -> {
            FacturaLegal fl = inv.getArgument(0);
            if (fl.getId() == null) fl.setId(100L);
            return fl;
        });
        when(facturaLegalItemService.save(any(FacturaLegalItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static FacturaLegalItemDescriptor descriptor(String descripcion, double cantidad, double precio) {
        FacturaLegalItemDescriptor d = new FacturaLegalItemDescriptor();
        d.setIva(10);
        d.setDescripcion(descripcion);
        d.setCantidad((float) cantidad);
        d.setPrecioUnitario(precio);
        d.setTotal(precio * cantidad);
        return d;
    }

    /** Como la arma FacturaService.crearFacturaLegalDesdeVenta: sin totalFinal y sin cliente. */
    private static FacturaLegal desdeVenta(double descuento) {
        FacturaLegal fl = new FacturaLegal();
        fl.setFecha(LocalDateTime.of(2026, 10, 11, 10, 0));
        fl.setNombre("SIN NOMBRE");
        fl.setRuc("X");
        fl.setDescuento(descuento);
        fl.setSucursalId(2L);
        fl.setCredito(false);
        return fl;
    }

    private void verificarEmitida(FacturaLegal result, double totalEsperado) throws Exception {
        assertEquals(totalEsperado, result.getTotalFinal(), 0.01);
        assertEquals(CDC, result.getCdc());
        assertEquals(Long.valueOf(5105L), timbradoDetalle.getNumeroActual());
        verify(sifenService, times(1)).validarGeneracionDE(any(FacturaLegal.class), any());
    }

    @Test
    void ventaMasTicketValida_noSeCorta() throws Exception {
        List<FacturaLegalItemDescriptor> items = Arrays.asList(descriptor("A", 3, 5000.0), descriptor("B", 1, 6000.0));

        FacturaLegal result = builder.build(new BuildRequest(desdeVenta(0.0), items, PDV, true));

        verificarEmitida(result, 21000.0);
    }

    @Test
    void ventaMasTicketValidaConDescuento_noSeCorta() throws Exception {
        List<FacturaLegalItemDescriptor> items = Arrays.asList(descriptor("A", 3, 5000.0), descriptor("B", 1, 6000.0));

        FacturaLegal result = builder.build(new BuildRequest(desdeVenta(1000.0), items, PDV, true));

        verificarEmitida(result, 20000.0);
    }

    /** Carga manual y silenciosa: el total viene del input y el builder lo recalcula. */
    @Test
    void cargaManualValidaConElTotalDelInput_noSeCorta() throws Exception {
        FacturaLegal fl = desdeVenta(0.0);
        fl.setTotalFinal(999999.0);

        FacturaLegal result = builder.build(new BuildRequest(fl,
                Collections.singletonList(descriptor("ITEM", 1, 11000.0)), PDV, true));

        verificarEmitida(result, 11000.0);
    }

    /** La API resuelve el detalle, manda generarDE=false y crea el DE por su cuenta. */
    @Test
    void apiValida_noSeCorta() throws Exception {
        FacturaLegal fl = desdeVenta(0.0);
        fl.setTotalFinal(11000.0);

        FacturaLegal result = builder.build(new BuildRequest(fl,
                Collections.singletonList(descriptor("ITEM", 1, 11000.0)), timbradoDetalle, false));

        assertEquals(11000.0, result.getTotalFinal(), 0.01);
        assertEquals(Long.valueOf(5105L), timbradoDetalle.getNumeroActual());
        verify(sifenService, times(1)).validarGeneracionDE(any(FacturaLegal.class), any());
        verify(sifenService, never()).crearDocumentoElectronico(any(FacturaLegal.class));
    }

    @Test
    void itemAPrecioCero_cortaSinEscribir() throws Exception {
        List<FacturaLegalItemDescriptor> items =
                Arrays.asList(descriptor("ITEM", 1, 11000.0), descriptor("REGALO", 1, 0.0));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> builder.build(new BuildRequest(desdeVenta(0.0), items, PDV, true)));

        assertTrue(e.getMessage().startsWith("FACTURA_NO_EMITIBLE"), e.getMessage());
        assertTrue(e.getMessage().contains("REGALO"), e.getMessage());
        verify(facturaLegalService, never()).save(any(FacturaLegal.class));
        verify(facturaLegalItemService, never()).save(any(FacturaLegalItem.class));
        verify(timbradoDetalleService, never()).save(any(TimbradoDetalle.class));
        verify(sifenService, never()).crearDocumentoElectronico(any(FacturaLegal.class));
        assertEquals(Long.valueOf(5104L), timbradoDetalle.getNumeroActual());
    }

    /** Rutinario: una venta grande sin nombre. Hoy pierde la venta; ahora se guarda sin factura. */
    @Test
    void innominadaDeSieteMillones_cortaSinEscribir() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> builder.build(new BuildRequest(desdeVenta(0.0),
                        Collections.singletonList(descriptor("TV", 1, 7000000.0)), PDV, true)));

        assertTrue(e.getMessage().startsWith("FACTURA_NO_EMITIBLE"), e.getMessage());
        verify(facturaLegalService, never()).save(any(FacturaLegal.class));
    }
}
