package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.financiero.ConteoGraphQL;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.graphql.financiero.GastoGraphQL;
import com.franco.dev.graphql.financiero.RetiroGraphQL;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.graphql.financiero.input.GastoInput;
import com.franco.dev.graphql.financiero.input.RetiroInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.DeliveryInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.financiero.ConfiguracionFacturacionLector;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.PoliticaFacturacionService;
import com.franco.dev.service.financiero.PoliticaFacturacionService.RutaVenta;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.VentaDuplicadaCacheService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import com.franco.dev.service.seguridad.AuditorUsuarioIdMocks;
import com.franco.dev.service.sorteo.SorteoParticipacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Donde se engancha {@link AuditorUsuarioId#firmante} (issue #142): en las altas, antes de todo lo que
 * lee el usuario, y cada campo que viaja junto al principal por su cuenta. Las ediciones de registros
 * que existen no lo llaman: reenvian a proposito al autor original. Un id inventado no es una edicion.
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
        AuditorUsuarioIdMocks.firmarElInput(auditorUsuarioId);
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
        when(auditorUsuarioId.firmanteSecundario(eq("VentaGraphQL.saveVenta"), eq(3L), eq(1L))).thenReturn(410L);
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
    void elCobroSeFirmaAunqueLaVentaYaVengaConLaSesion() throws Exception {
        when(auditorUsuarioId.firmanteSecundario(eq("VentaGraphQL.saveVenta"), eq(3L), any())).thenReturn(410L);
        VentaInput vi = new VentaInput();
        vi.setUsuarioId(410L);
        vi.setCajaId(1L);
        CobroInput ci = new CobroInput();
        ci.setUsuarioId(3L);

        ventaGraphQL.saveVenta(vi, Collections.singletonList(new VentaItemInput()), ci, new ArrayList<>(), true,
                false, "impresora", "local", 1L, null, null, false, null);

        verify(cobroGraphQL).saveCobro(argThat(c -> Long.valueOf(410L).equals(c.getUsuarioId())), any(), any());
    }

    @Test
    void ventaSinItemsDeUnaVentaQueExisteEsEdicionYNoReemplaza() throws Exception {
        doReturn(new Venta()).when(ventaGraphQL).saveVenta2(any());
        when(service.findById(77L)).thenReturn(Optional.of(new Venta()));
        VentaInput vi = new VentaInput();
        vi.setId(77L);
        vi.setUsuarioId(3L);

        ventaGraphQL.saveVenta(vi, null, null, null, true, false, "impresora", "local",
                1L, null, null, false, null);

        verify(auditorUsuarioId, never()).firmante(any(), any(), any());
    }

    @Test
    void ventaSinItemsConUnIdInventadoSeFirma() throws Exception {
        doReturn(new Venta()).when(ventaGraphQL).saveVenta2(any());
        when(service.findById(999L)).thenReturn(Optional.empty());
        VentaInput vi = new VentaInput();
        vi.setId(999L);
        vi.setUsuarioId(3L);

        ventaGraphQL.saveVenta(vi, null, null, null, true, false, "impresora", "local",
                1L, null, null, false, null);

        verify(auditorUsuarioId).firmante(eq("VentaGraphQL.saveVenta"), eq(3L), any());
    }

    private static GastoGraphQL gastoCon(AuditorUsuarioId auditor, Long idExistente) {
        GastoGraphQL gasto = new GastoGraphQL();
        GastoService gastos = mock(GastoService.class);
        when(gastos.findById(any())).thenReturn(Optional.empty());
        if (idExistente != null) when(gastos.findById(idExistente)).thenReturn(Optional.of(new Gasto()));
        ReflectionTestUtils.setField(gasto, "auditorUsuarioId", auditor);
        ReflectionTestUtils.setField(gasto, "service", gastos);
        return gasto;
    }

    @Test
    void editarUnGastoQueExisteNoReemplazaAlAutor() {
        AuditorUsuarioId auditor = AuditorUsuarioIdMocks.queFirmaElInput();
        GastoInput input = new GastoInput();
        input.setId(5L);
        input.setUsuarioId(3L);

        // El resto del metodo necesita servicios que aca no hay; lo que importa pasa antes.
        try {
            gastoCon(auditor, 5L).saveGasto(input, null, null, false);
        } catch (RuntimeException ignorada) {
        }

        verify(auditor, never()).firmante(any(), any(), any());
        verify(auditor).verificar("GastoGraphQL.saveGasto", 3L);
    }

    @Test
    void unGastoConIdInventadoSeFirma() {
        AuditorUsuarioId auditor = AuditorUsuarioIdMocks.queFirmaElInput();
        GastoInput input = new GastoInput();
        input.setId(999999L);
        input.setUsuarioId(3L);
        input.setCajaId(1L);

        try {
            gastoCon(auditor, null).saveGasto(input, null, null, false);
        } catch (RuntimeException ignorada) {
        }

        verify(auditor).firmante("GastoGraphQL.saveGasto", 3L, 1L);
    }

    @Test
    void altaDeRetiroSeFirmaConLaCajaDeSalida() {
        AuditorUsuarioId auditor = AuditorUsuarioIdMocks.queFirmaElInput();
        RetiroGraphQL retiro = new RetiroGraphQL();
        RetiroService retiros = mock(RetiroService.class);
        when(retiros.findById(any())).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(retiro, "auditorUsuarioId", auditor);
        ReflectionTestUtils.setField(retiro, "service", retiros);
        RetiroInput input = new RetiroInput();
        input.setId(999999L); // inventado: no es una edicion
        input.setUsuarioId(3L);
        input.setCajaSalidaId(1185L);

        try {
            retiro.saveRetiro(input, new ArrayList<>(), null, null, true);
        } catch (RuntimeException ignorada) {
        }

        verify(auditor).firmante("RetiroGraphQL.saveRetiro", 3L, 1185L);
        verify(auditor, never()).verificar(any(), any());
    }

    @Test
    void conteoSeFirmaConSuCaja() {
        AuditorUsuarioId auditor = AuditorUsuarioIdMocks.queFirmaElInput();
        ConteoGraphQL conteo = new ConteoGraphQL();
        ReflectionTestUtils.setField(conteo, "auditorUsuarioId", auditor);
        ConteoInput input = new ConteoInput();
        input.setUsuarioId(3L);

        try {
            conteo.saveConteo(input, new ArrayList<>(), 1194L, true, false);
        } catch (RuntimeException ignorada) {
        }

        verify(auditor).firmante("ConteoGraphQL.saveConteo", 3L, 1194L);
    }

    @Test
    void cadaItemDeLaFacturaSeFirmaPorSuCuenta() {
        AuditorUsuarioId auditor = AuditorUsuarioIdMocks.queFirmaElInput();
        FacturaLegalGraphQL factura = new FacturaLegalGraphQL();
        ReflectionTestUtils.setField(factura, "auditorUsuarioId", auditor);
        FacturaLegalInput input = new FacturaLegalInput();
        input.setUsuarioId(410L);
        input.setCajaId(1185L);
        FacturaLegalItemInput item = new FacturaLegalItemInput();
        item.setUsuarioId(3L);

        try {
            factura.saveFacturaLegal(input, Collections.singletonList(item), null, 25, false);
        } catch (RuntimeException ignorada) {
        }

        verify(auditor).firmante("FacturaLegalGraphQL.saveFacturaLegal", 410L, 1185L);
        verify(auditor).firmanteSecundario("FacturaLegalGraphQL.saveFacturaLegal", 3L, 1185L);
    }

    @Test
    void altaDeDeliveryFirmaVentaYCobroPorSuCuenta() {
        AuditorUsuarioId auditor = AuditorUsuarioIdMocks.queFirmaElInput();
        DeliveryGraphQL delivery = new DeliveryGraphQL();
        ReflectionTestUtils.setField(delivery, "auditorUsuarioId", auditor);
        DeliveryInput input = new DeliveryInput();
        input.setUsuarioId(410L);
        VentaInput venta = new VentaInput();
        venta.setUsuarioId(3L);
        venta.setCajaId(1185L);
        CobroInput cobro = new CobroInput();
        cobro.setUsuarioId(3L);

        try {
            delivery.saveDeliveryAndVenta(input, venta, null, null, null, cobro, null);
        } catch (Exception ignorada) {
        }

        verify(auditor).firmante("DeliveryGraphQL.saveDeliveryAndVenta", 410L, 1185L);
        verify(auditor, times(2)).firmanteSecundario("DeliveryGraphQL.saveDeliveryAndVenta", 3L, 1185L);
    }

    @Test
    void editarUnDeliveryNoReemplazaAlAutor() {
        AuditorUsuarioId auditor = AuditorUsuarioIdMocks.queFirmaElInput();
        DeliveryGraphQL delivery = new DeliveryGraphQL();
        ReflectionTestUtils.setField(delivery, "auditorUsuarioId", auditor);
        DeliveryInput input = new DeliveryInput();
        input.setId(9L);
        input.setUsuarioId(3L);

        try {
            delivery.saveDeliveryAndVenta(input, new VentaInput(), null, null, null, null, null);
        } catch (Exception ignorada) {
        }

        verify(auditor, never()).firmante(any(), any(), any());
        verify(auditor, never()).firmanteSecundario(any(), any(), any());
    }
}
