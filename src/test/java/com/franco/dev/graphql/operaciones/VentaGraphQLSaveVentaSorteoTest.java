package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.SorteoClienteInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.financiero.ConfiguracionFacturacionLector;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.PoliticaFacturacionService;
import com.franco.dev.service.financiero.PoliticaFacturacionService.RutaVenta;
import com.franco.dev.service.financiero.builder.VentaNoFacturableException;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.VentaDuplicadaCacheService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import com.franco.dev.service.sorteo.SorteoParticipacionService;
import com.franco.dev.service.sorteo.SorteoParticipacionService.Participacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * saveVenta de punta a punta (con mocks) en lo que toca al sorteo: si el comprobante falla y la
 * venta se confirma igual, la participacion se registra igual; si la venta se rechaza
 * (VentaNoFacturableException), no.
 */
class VentaGraphQLSaveVentaSorteoTest {

    @Mock private AuditorUsuarioId auditorUsuarioId;
    @Mock private VentaDuplicadaCacheService ventaDuplicadaCacheService;
    @Mock private ConfiguracionFacturacionLector configuracionFacturacionLector;
    @Mock private PoliticaFacturacionService politicaFacturacionService;
    @Mock private CobroGraphQL cobroGraphQL;
    @Mock private VentaItemGraphQL ventaItemGraphQL;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private VentaService service;
    @Mock private Environment env;
    @Mock private UsuarioService usuarioService;
    @Mock private ClienteService clienteService;
    @Mock private FormaPagoService formaPagoService;
    @Mock private SorteoParticipacionService sorteoParticipacionService;
    @Mock private ImpresionService impresionService;
    @Spy @InjectMocks private VentaGraphQL ventaGraphQL;

    private final List<CobroDetalleInput> detalles = new ArrayList<>();
    private final SorteoClienteInput cliente = new SorteoClienteInput();
    private Venta guardada;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(ventaDuplicadaCacheService.reservar(any(), any()))
                .thenReturn(mock(VentaDuplicadaCacheService.Reserva.class));
        when(cobroGraphQL.saveCobro(any(), any(), any())).thenReturn(new Cobro());
        when(pdvCajaService.findById(any())).thenReturn(Optional.of(new PdvCaja()));
        when(env.getProperty("sucursalId")).thenReturn("7");
        guardada = new Venta();
        guardada.setId(100L);
        guardada.setSucursalId(7L);
        guardada.setTotalGs(500000d);
        when(service.saveAndSend(any(), eq(false))).thenReturn(guardada);
        when(ventaItemGraphQL.saveVentaItemList(any(), any())).thenReturn(new ArrayList<>());
        when(politicaFacturacionService.decidirRuta(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(RutaVenta.TICKET_SIMPLE);
        Participacion p = new Participacion();
        p.setNombreSorteo("NAVIDAD");
        p.setCantidad(2);
        when(sorteoParticipacionService.registrar(any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(p));
        when(impresionService.printSorteoTickets(anyList(), any(), any(), any())).thenReturn(true);
        cliente.setNombre("JUAN");
    }

    private Venta saveVenta() throws Exception {
        VentaInput vi = new VentaInput();
        vi.setUsuarioId(3L);
        vi.setCajaId(1L);
        List<VentaItemInput> items = Collections.singletonList(new VentaItemInput());
        return ventaGraphQL.saveVenta(vi, items, new CobroInput(), detalles, true, false, "impresora", "local",
                1L, null, null, false, cliente);
    }

    @Test
    void comprobanteOkRegistraElSorteo() throws Exception {
        doReturn(true).when(ventaGraphQL).printTicket58mm(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        Venta v = saveVenta();
        verify(sorteoParticipacionService).registrar(same(guardada), same(detalles), same(cliente), eq(3L));
        assertEquals(1, v.getSorteoTickets().size());
    }

    @Test
    void siElComprobanteFallaLaParticipacionSeRegistraIgual() throws Exception {
        doThrow(new RuntimeException("sin papel")).when(ventaGraphQL).printTicket58mm(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
        Venta v = saveVenta();
        assertSame(guardada, v);
        verify(sorteoParticipacionService).registrar(same(guardada), same(detalles), same(cliente), eq(3L));
        assertEquals(1, v.getSorteoTickets().size());
    }

    @Test
    void ventaRechazadaNoRegistraElSorteo() throws Exception {
        doThrow(new VentaNoFacturableException("no facturable")).when(ventaGraphQL).printTicket58mm(any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThrows(VentaNoFacturableException.class, this::saveVenta);
        verify(sorteoParticipacionService, never()).registrar(any(), any(), any(), any());
        verifyNoInteractions(impresionService);
    }

    @Test
    void transaccionYaRevertidaNoRegistraNiImprime() throws Exception {
        doReturn(true).when(ventaGraphQL).printTicket58mm(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        doReturn(true).when(ventaGraphQL).ventaVaARevertirse();
        Venta v = saveVenta();
        verify(sorteoParticipacionService, never()).registrar(any(), any(), any(), any());
        verifyNoInteractions(impresionService);
        assertNull(v.getSorteoTickets());
    }
}
