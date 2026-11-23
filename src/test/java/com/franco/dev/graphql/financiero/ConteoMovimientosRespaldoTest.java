package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MonedaBilletes;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.ConteoService;
import com.franco.dev.service.financiero.MonedaBilleteService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioIdMocks;
import com.franco.dev.service.seguridad.RolSesionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Filas de respaldo de un conteo en movimiento_caja (issue #80): por moneda, solo si el total de esa moneda no es 0
 * y el input no trae billetes de esa moneda con cantidad. Es lo que lee el balance cuando no hay conteo_moneda.
 */
class ConteoMovimientosRespaldoTest {

    private static final Long CAJA = 50L;
    private static final long BILLETE_GS = 1L;

    private final Moneda guarani = moneda(1L, "GUARANI");
    private final Moneda real = moneda(2L, "REAL");
    private final Moneda dolar = moneda(3L, "DOLAR");
    private MovimientoCajaService movimientoCajaService;
    private ConteoGraphQL resolver;

    @BeforeEach
    void setUp() {
        ConteoService service = mock(ConteoService.class);
        PdvCajaService pdvCajaService = mock(PdvCajaService.class);
        SucursalService sucursalService = mock(SucursalService.class);
        MonedaService monedaService = mock(MonedaService.class);
        MonedaBilleteService monedaBilleteService = mock(MonedaBilleteService.class);
        movimientoCajaService = mock(MovimientoCajaService.class);

        resolver = new ConteoGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "eventoEmisor", org.mockito.Mockito.mock(com.franco.dev.eventos.EventoEmisor.class));
        ReflectionTestUtils.setField(resolver, "conteoMonedaService", org.mockito.Mockito.mock(com.franco.dev.service.financiero.ConteoMonedaService.class));
        ReflectionTestUtils.setField(resolver, "pdvCajaService", pdvCajaService);
        ReflectionTestUtils.setField(resolver, "sucursalService", sucursalService);
        ReflectionTestUtils.setField(resolver, "monedaService", monedaService);
        ReflectionTestUtils.setField(resolver, "monedaBilleteService", monedaBilleteService);
        ReflectionTestUtils.setField(resolver, "movimientoCajaService", movimientoCajaService);
        ReflectionTestUtils.setField(resolver, "cambioService", mock(CambioService.class));
        ReflectionTestUtils.setField(resolver, "usuarioService", mock(UsuarioService.class));
        ReflectionTestUtils.setField(resolver, "auditorUsuarioId", AuditorUsuarioIdMocks.queFirmaElInput());
        ReflectionTestUtils.setField(resolver, "conteoMonedaGraphQL", mock(ConteoMonedaGraphQL.class));
        ReflectionTestUtils.setField(resolver, "rolSesionService", mock(RolSesionService.class));

        Sucursal sucursal = new Sucursal();
        sucursal.setId(3L);
        when(sucursalService.sucursalActual()).thenReturn(sucursal);
        when(monedaService.findAll2()).thenReturn(Arrays.asList(guarani, real, dolar));
        MonedaBilletes billeteGs = new MonedaBilletes();
        billeteGs.setMoneda(guarani);
        when(monedaBilleteService.findById(BILLETE_GS)).thenReturn(Optional.of(billeteGs));
        PdvCaja caja = new PdvCaja();
        caja.setId(CAJA);
        when(pdvCajaService.findByIdForUpdate(CAJA)).thenReturn(Optional.of(caja));
        when(service.saveAndSend(any(Conteo.class), anyBoolean())).thenAnswer(inv -> {
            Conteo c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });
    }

    @Test
    void conBilletesDeGuaraniesYUnTotalEnRealesSinBilletesQuedaSoloLaFilaDeReales() {
        resolver.saveConteo(totales(100_000, 50, 0), Collections.singletonList(billeteGs(2)), CAJA, true, false);

        List<MovimientoCaja> filas = filasEscritas();
        assertEquals(1, filas.size());
        assertSame(real, filas.get(0).getMoneda());
        assertEquals(50.0, filas.get(0).getCantidad());
    }

    @Test
    void billetesEnCantidadCeroNoCuentanComoBilletes() {
        resolver.saveConteo(totales(100_000, 0, 0), Collections.singletonList(billeteGs(0)), CAJA, true, false);

        List<MovimientoCaja> filas = filasEscritas();
        assertEquals(1, filas.size());
        assertSame(guarani, filas.get(0).getMoneda());
    }

    @Test
    void unConteoConBilletesYSinOtrosTotalesNoEscribeFilas() {
        resolver.saveConteo(totales(100_000, 0, 0), Collections.singletonList(billeteGs(2)), CAJA, true, false);

        verify(movimientoCajaService, never()).saveAndSend(any(MovimientoCaja.class), anyBoolean());
    }

    @Test
    void unConteoEnCeroNoEscribeFilas() {
        resolver.saveConteo(totales(0, 0, 0), null, CAJA, true, false);

        verify(movimientoCajaService, never()).saveAndSend(any(MovimientoCaja.class), anyBoolean());
    }

    @Test
    void sinBilletesYConTotalesQuedanLasFilasComoHoy() {
        resolver.saveConteo(totales(100_000, 0, 20), null, CAJA, true, false);

        List<Moneda> monedas = filasEscritas().stream().map(MovimientoCaja::getMoneda).collect(Collectors.toList());
        assertEquals(Arrays.asList(guarani, dolar), monedas);
    }

    private List<MovimientoCaja> filasEscritas() {
        ArgumentCaptor<MovimientoCaja> captor = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaService, atLeast(0)).saveAndSend(captor.capture(), anyBoolean());
        return captor.getAllValues();
    }

    private static ConteoInput totales(double gs, double rs, double ds) {
        ConteoInput input = new ConteoInput();
        input.setTotalGs(gs);
        input.setTotalRs(rs);
        input.setTotalDs(ds);
        return input;
    }

    private static ConteoMonedaInput billeteGs(double cantidad) {
        ConteoMonedaInput b = new ConteoMonedaInput();
        b.setMonedaBilletesId(BILLETE_GS);
        b.setCantidad(cantidad);
        return b;
    }

    private static Moneda moneda(Long id, String denominacion) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        return m;
    }
}
