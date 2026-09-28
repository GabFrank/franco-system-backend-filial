package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CapturaCupon;
import com.franco.dev.domain.financiero.ConfiguracionVentaTarjeta;
import com.franco.dev.repository.financiero.CapturaCuponRepository;
import com.franco.dev.repository.financiero.VentaTarjetaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Fija el criterio de la purga.
 *
 * <p>El caso que mas importa es el que la auditoria del plan marco: <b>una foto atada a una venta
 * no se borra</b>. Hasta que {@code completar()} empezo a copiar la ruta a
 * {@code venta_tarjeta.imagen_url}, no habia forma de distinguirla de una huerfana, y una purga
 * por antiguedad se llevaba puesta la evidencia de un cobro.
 */
public class PurgaImagenesCuponServiceTest {

    /** JUnit 5 de este repo no trae @TempDir, asi que la carpeta se maneja a mano. */
    private Path carpeta;

    private CapturaCuponRepository capturas;
    private VentaTarjetaRepository ventas;
    private ConfiguracionVentaTarjetaService configuracion;
    private PurgaImagenesCuponService service;

    @BeforeEach
    public void setUp() throws Exception {
        carpeta = Files.createTempDirectory("purga-test");
        capturas = mock(CapturaCuponRepository.class);
        ventas = mock(VentaTarjetaRepository.class);
        configuracion = mock(ConfiguracionVentaTarjetaService.class);
        service = new PurgaImagenesCuponService(capturas, ventas, configuracion,
                carpeta.toAbsolutePath().toString());
    }

    @AfterEach
    public void limpiar() throws Exception {
        File[] hijos = carpeta.toFile().listFiles();
        if (hijos != null) for (File f : hijos) f.delete();
        carpeta.toFile().delete();
    }

    private void conRetencionDe(Integer dias) {
        ConfiguracionVentaTarjeta c = new ConfiguracionVentaTarjeta();
        c.setDiasRetencionImagenes(dias);
        when(configuracion.findOrDefault()).thenReturn(c);
    }

    private CapturaCupon captura(String nombre) throws Exception {
        File f = carpeta.resolve(nombre).toFile();
        Files.write(f.toPath(), new byte[]{1, 2, 3, 4, 5});
        CapturaCupon c = new CapturaCupon();
        c.setId(1L);
        c.setImagenUrl(f.getAbsolutePath());
        return c;
    }

    @Test
    public void sin_dias_de_retencion_no_se_borra_nada() throws Exception {
        // NULL es el default de la columna y significa "no purgar nunca". Una purga que se prende
        // sola porque alguien dejo el campo vacio es justo la sorpresa que no puede pasar.
        conRetencionDe(null);
        CapturaCupon c = captura("a.jpg");

        PurgaImagenesCuponService.Resumen r = service.purgar(false);

        assertEquals(0, r.borradas);
        assertTrue(new File(c.getImagenUrl()).exists());
        verify(capturas, never()).findByCreadoEnBeforeAndImagenUrlIsNotNull(any());
    }

    @Test
    public void una_retencion_de_cero_o_negativa_tampoco_purga() {
        conRetencionDe(0);
        assertEquals(0, service.purgar(false).borradas);
        conRetencionDe(-5);
        assertEquals(0, service.purgar(false).borradas);
    }

    @Test
    public void una_foto_huerfana_vieja_se_borra() throws Exception {
        conRetencionDe(30);
        CapturaCupon c = captura("huerfana.jpg");
        when(capturas.findByCreadoEnBeforeAndImagenUrlIsNotNull(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(c));
        when(ventas.existsByImagenUrl(anyString())).thenReturn(false);

        PurgaImagenesCuponService.Resumen r = service.purgar(false);

        assertEquals(1, r.borradas);
        assertEquals(0, r.protegidas);
        assertEquals(5, r.bytesLiberados);
        assertFalse(new File(c.getImagenUrl()).exists());
    }

    @Test
    public void una_foto_atada_a_una_venta_NO_se_borra_por_vieja_que_sea() throws Exception {
        // El hallazgo de la auditoria del plan, hecho test.
        conRetencionDe(1);
        CapturaCupon c = captura("evidencia.jpg");
        when(capturas.findByCreadoEnBeforeAndImagenUrlIsNotNull(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(c));
        when(ventas.existsByImagenUrl(c.getImagenUrl())).thenReturn(true);

        PurgaImagenesCuponService.Resumen r = service.purgar(false);

        assertEquals(0, r.borradas);
        assertEquals(1, r.protegidas);
        assertTrue(new File(c.getImagenUrl()).exists(), "la evidencia de un cobro no se toca");
    }

    @Test
    public void en_simulacion_cuenta_pero_no_borra() throws Exception {
        conRetencionDe(30);
        CapturaCupon c = captura("simulada.jpg");
        when(capturas.findByCreadoEnBeforeAndImagenUrlIsNotNull(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(c));
        when(ventas.existsByImagenUrl(anyString())).thenReturn(false);

        PurgaImagenesCuponService.Resumen r = service.purgar(true);

        assertTrue(r.simulacion);
        assertEquals(1, r.borradas, "tiene que decir cuantas borraria");
        assertTrue(new File(c.getImagenUrl()).exists(), "pero no tocar el archivo");
    }

    @Test
    public void una_ruta_fuera_de_la_carpeta_no_se_toca() throws Exception {
        // Una ruta guardada mal --o manipulada-- no tiene por que poder borrar cualquier archivo
        // del filial. Incluye el caso del `..` en el medio, que sin normalizar pasaria el chequeo.
        conRetencionDe(30);
        File afuera = File.createTempFile("fuera-de-la-carpeta", ".jpg");
        afuera.deleteOnExit();

        CapturaCupon c = new CapturaCupon();
        c.setId(9L);
        c.setImagenUrl(carpeta.resolve("..").resolve(afuera.getName()).toString());
        when(capturas.findByCreadoEnBeforeAndImagenUrlIsNotNull(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(c));
        when(ventas.existsByImagenUrl(anyString())).thenReturn(false);

        PurgaImagenesCuponService.Resumen r = service.purgar(false);

        assertEquals(0, r.borradas);
        assertTrue(afuera.exists(), "no deberia salir de la carpeta de capturas");
    }

    @Test
    public void un_archivo_que_ya_no_esta_no_cuenta_ni_rompe() throws Exception {
        conRetencionDe(30);
        CapturaCupon c = new CapturaCupon();
        c.setId(3L);
        c.setImagenUrl(carpeta.resolve("nunca-existio.jpg").toString());
        when(capturas.findByCreadoEnBeforeAndImagenUrlIsNotNull(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(c));
        when(ventas.existsByImagenUrl(anyString())).thenReturn(false);

        PurgaImagenesCuponService.Resumen r = service.purgar(false);

        assertEquals(0, r.borradas);
        assertEquals(1, r.candidatas);
    }

    @Test
    public void mezcla_de_huerfanas_y_protegidas() throws Exception {
        conRetencionDe(30);
        CapturaCupon a = captura("a.jpg");
        CapturaCupon b = captura("b.jpg");
        when(capturas.findByCreadoEnBeforeAndImagenUrlIsNotNull(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(a, b));
        when(ventas.existsByImagenUrl(a.getImagenUrl())).thenReturn(true);
        when(ventas.existsByImagenUrl(b.getImagenUrl())).thenReturn(false);

        PurgaImagenesCuponService.Resumen r = service.purgar(false);

        assertEquals(2, r.candidatas);
        assertEquals(1, r.protegidas);
        assertEquals(1, r.borradas);
        assertTrue(new File(a.getImagenUrl()).exists());
        assertFalse(new File(b.getImagenUrl()).exists());
    }
}
