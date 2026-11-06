package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.financiero.GastoGraphQL;
import com.franco.dev.graphql.financiero.input.GastoInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.DeliveryInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.financiero.ConfiguracionFacturacionLector;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.PoliticaFacturacionService;
import com.franco.dev.service.financiero.PoliticaFacturacionService.RutaVenta;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.VentaDuplicadaCacheService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import com.franco.dev.service.sorteo.SorteoParticipacionService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Donde se engancha {@link AuditorUsuarioId#firmante} (issue #142): en las altas, y antes de todo lo
 * que lee el usuario. Las ediciones no lo llaman: reenvian a proposito al autor original.
 */
class UsuarioFirmanteEnganchesTest {

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

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(ventaDuplicadaCacheService.reservar(any(), any()))
                .thenReturn(mock(VentaDuplicadaCacheService.Reserva.class));
        when(cobroGraphQL.saveCobro(any(), any(), any())).thenReturn(new Cobro());
        when(pdvCajaService.findById(any())).thenReturn(Optional.of(new PdvCaja()));
        when(env.getProperty("sucursalId")).thenReturn("7");
        Venta guardada = new Venta();
        guardada.setId(100L);
        guardada.setSucursalId(7L);
        guardada.setTotalGs(500000d);
        when(service.saveAndSend(any(), eq(false))).thenReturn(guardada);
        when(ventaItemGraphQL.saveVentaItemList(any(), any())).thenReturn(new ArrayList<>());
        when(politicaFacturacionService.decidirRuta(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(RutaVenta.TICKET_SIMPLE);
        doReturn(true).when(ventaGraphQL).printTicket58mm(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void altaDeVentaFirmaConLaSesionAntesDeLaHuellaYDelCobro() throws Exception {
        when(auditorUsuarioId.firmante(eq("VentaGraphQL.saveVenta"), eq(3L), eq(1L))).thenReturn(410L);
        VentaInput vi = new VentaInput();
        vi.setUsuarioId(3L);
        vi.setCajaId(1L);
        CobroInput ci = new CobroInput();
        ci.setUsuarioId(3L);
        List<VentaItemInput> items = Collections.singletonList(new VentaItemInput());
        List<CobroDetalleInput> detalles = new ArrayList<>();

        ventaGraphQL.saveVenta(vi, items, ci, detalles, true, false, "impresora", "local",
                1L, null, null, false, null);

        verify(ventaDuplicadaCacheService).reservar(eq(410L), any());
        verify(cobroGraphQL).saveCobro(argThat(c -> Long.valueOf(410L).equals(c.getUsuarioId())), any(), any());
    }

    @Test
    void ventaSinItemsEsEdicionYNoReemplaza() throws Exception {
        doReturn(new Venta()).when(ventaGraphQL).saveVenta2(any());
        VentaInput vi = new VentaInput();
        vi.setUsuarioId(3L);

        ventaGraphQL.saveVenta(vi, null, null, null, true, false, "impresora", "local",
                1L, null, null, false, null);

        verify(auditorUsuarioId, never()).firmante(any(), any(), any());
    }

    @Test
    void editarUnGastoNoReemplazaAlAutor() {
        AuditorUsuarioId auditor = mock(AuditorUsuarioId.class);
        GastoGraphQL gasto = new GastoGraphQL();
        org.springframework.test.util.ReflectionTestUtils.setField(gasto, "auditorUsuarioId", auditor);
        GastoInput input = new GastoInput();
        input.setId(5L);
        input.setUsuarioId(3L);

        // El resto del metodo necesita servicios que aca no hay; lo que importa pasa antes.
        try {
            gasto.saveGasto(input, null, null, false);
        } catch (RuntimeException ignorada) {
        }

        verify(auditor, never()).firmante(any(), any(), any());
        verify(auditor).verificar("GastoGraphQL.saveGasto", 3L);
    }

    @Test
    void editarUnDeliveryNoReemplazaAlAutor() {
        AuditorUsuarioId auditor = mock(AuditorUsuarioId.class);
        DeliveryGraphQL delivery = new DeliveryGraphQL();
        org.springframework.test.util.ReflectionTestUtils.setField(delivery, "auditorUsuarioId", auditor);
        DeliveryInput input = new DeliveryInput();
        input.setId(9L);
        input.setUsuarioId(3L);

        try {
            delivery.saveDeliveryAndVenta(input, new VentaInput(), null, null, null, null, null);
        } catch (Exception ignorada) {
        }

        verify(auditor, never()).firmante(any(), any(), any());
    }
}
