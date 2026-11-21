package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.domain.financiero.ConfiguracionVentaTarjeta;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.repository.financiero.CapturaCuponRepository;
import com.franco.dev.repository.financiero.FormatoTerminalPosRegionRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.ocr.CuponOcrService;
import com.franco.dev.service.financiero.ocr.DerivadorMapa;
import com.franco.dev.service.financiero.ocr.ExtractorCupon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #151: con el modulo apagado el filial no abre, no procesa ni deriva capturas.
 * <p>
 * Caja abierta y OCR disponible van mockeados a proposito: con los mocks por defecto la caja no se
 * encuentra y {@code crear}/{@code procesar} ya lanzaban «la caja no esta abierta», asi que un test
 * que solo esperara una IllegalStateException pasaba igual sin la guarda. Por eso se compara el
 * mensaje.
 */
class CapturaCuponHabilitadoTest {

    private static final String APAGADO = "la venta con tarjeta esta deshabilitada";
    private static final String TOKEN = "tok";

    private CapturaCuponRepository repository;
    private CuponOcrService ocr;
    private ConfiguracionVentaTarjetaService configuracion;
    private CapturaCuponService service;

    @BeforeEach
    void armar() {
        repository = mock(CapturaCuponRepository.class);
        ocr = mock(CuponOcrService.class);
        configuracion = mock(ConfiguracionVentaTarjetaService.class);
        SucursalService sucursales = mock(SucursalService.class);
        PdvCajaService cajas = mock(PdvCajaService.class);

        PdvCaja abierta = new PdvCaja();
        abierta.setActivo(true);
        when(cajas.findById(anyLong())).thenReturn(Optional.of(abierta));
        when(sucursales.exigirSucursalPropia(any())).thenReturn(24L);
        when(ocr.disponible()).thenReturn(true);
        when(repository.save(any(CapturaCupon.class))).thenAnswer(i -> i.getArgument(0));

        service = new CapturaCuponService(repository, ocr, mock(ExtractorCupon.class), sucursales,
                mock(FormatoTerminalPosRegionRepository.class), mock(DerivadorMapa.class), cajas,
                configuracion, "cupones", "", 8082);
    }

    private void modulo(Boolean habilitado) {
        ConfiguracionVentaTarjeta c = new ConfiguracionVentaTarjeta();
        c.setHabilitado(habilitado);
        when(configuracion.findOrDefault()).thenReturn(c);
    }

    private CapturaCupon captura(String estado) {
        CapturaCupon c = new CapturaCupon();
        c.setId(1L);
        c.setToken(TOKEN);
        c.setCajaId(10L);
        c.setEstado(estado);
        c.setExpiraEn(LocalDateTime.now().plusMinutes(5));
        when(repository.lockByToken(TOKEN)).thenReturn(Optional.of(c));
        when(repository.findByToken(TOKEN)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void crearConModuloApagadoNoGuarda() {
        modulo(false);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.crear(10L, 24L, null, null));
        assertEquals(APAGADO, e.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void crearSinFilaReplicadaNoGuarda() {
        // El default de la entidad es el mismo que devuelve findOrDefault sin fila: deshabilitado.
        when(configuracion.findOrDefault()).thenReturn(new ConfiguracionVentaTarjeta());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.crear(10L, 24L, null, null));
        assertEquals(APAGADO, e.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void crearConModuloPrendidoGuarda() {
        modulo(true);
        CapturaCupon c = service.crear(10L, 24L, null, null);
        assertEquals(CapturaCupon.ESPERANDO, c.getEstado());
        verify(repository).save(c);
    }

    @Test
    void procesarTokenVigenteConModuloApagadoNoTocaLaCaptura() throws Exception {
        modulo(false);
        CapturaCupon c = captura(CapturaCupon.ESPERANDO);
        Integer intentosAntes = c.getIntentos();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.procesar(TOKEN, new byte[]{1}, null));
        assertEquals(APAGADO, e.getMessage());
        assertEquals(CapturaCupon.ESPERANDO, c.getEstado());
        assertEquals(intentosAntes, c.getIntentos());
        assertNull(c.getUsadoEn());
        verify(ocr, never()).leer(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void procesarCapturaEnErrorConModuloApagadoRechaza() {
        modulo(false);
        captura(CapturaCupon.ERROR);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.procesar(TOKEN, new byte[]{1}, null));
        assertEquals(APAGADO, e.getMessage());
    }

    @Test
    void procesarCapturaYaListaDevuelveElResultadoAunqueEsteApagado() {
        modulo(false);
        CapturaCupon c = captura(CapturaCupon.LISTO);
        assertSame(c, service.procesar(TOKEN, new byte[]{1}, null));
    }

    @Test
    void derivarMapaConModuloApagadoRechaza() {
        modulo(false);
        captura(CapturaCupon.LISTO);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.derivarMapa(TOKEN));
        assertEquals(APAGADO, e.getMessage());
    }
}
