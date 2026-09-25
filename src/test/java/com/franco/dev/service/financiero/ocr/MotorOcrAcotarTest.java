package com.franco.dev.service.financiero.ocr;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fija el filtro que acota el reconocimiento a las zonas del mapa.
 *
 * <p>Es la palanca de rendimiento del modulo --reconocer 6 cajas en vez de 26 baja {@code rec} de
 * 3.841 a ~900 ms-- pero tambien es lo que puede hacer <b>desaparecer</b> un campo si el mapa no
 * corresponde a la foto. Por eso los casos que mas importan son los de seguridad: sin zonas no
 * filtra, y si el filtro deja cero tampoco.
 */
public class MotorOcrAcotarTest {

    private static MotorOcr.Zona zona(double x1, double y1, double x2, double y2) {
        return new MotorOcr.Zona(x1, y1, x2, y2);
    }

    private static DetectorCajas.Caja caja(double x1, double y1, double x2, double y2) {
        // El constructor es package-private; este test vive en el mismo paquete a proposito.
        return new DetectorCajas.Caja(new double[][]{{x1, y1}, {x2, y1}, {x2, y2}, {x1, y2}}, 0.9);
    }

    /** `acotar` es privado: es detalle interno del motor, pero su comportamiento es critico. */
    @SuppressWarnings("unchecked")
    private static List<DetectorCajas.Caja> acotar(List<DetectorCajas.Caja> cajas,
                                                   List<MotorOcr.Zona> zonas, int w, int h) {
        try {
            Method m = MotorOcr.class.getDeclaredMethod("acotar", List.class, List.class, int.class, int.class);
            m.setAccessible(true);
            return (List<DetectorCajas.Caja>) m.invoke(null, cajas, zonas, w, h);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<DetectorCajas.Caja> cupon() {
        return new ArrayList<DetectorCajas.Caja>(Arrays.asList(
                caja(10, 10, 300, 30),     // encabezado, lejos de todo
                caja(10, 50, 60, 70),      // AUT:
                caja(80, 50, 180, 70),     // el valor
                caja(10, 400, 300, 420)    // pie, bien abajo
        ));
    }

    @Test
    public void sin_zonas_no_filtra_nada() {
        List<DetectorCajas.Caja> todas = cupon();
        assertEquals(4, acotar(todas, null, 400, 500).size());
        assertEquals(4, acotar(todas, new ArrayList<MotorOcr.Zona>(), 400, 500).size());
    }

    @Test
    public void deja_solo_las_cajas_que_tocan_alguna_zona() {
        // La zona cubre el renglon del AUT (y 50..70 sobre 500 = 0.10..0.14).
        List<MotorOcr.Zona> zonas = Arrays.asList(zona(0.0, 0.10, 0.50, 0.14));

        List<DetectorCajas.Caja> filtradas = acotar(cupon(), zonas, 400, 500);

        assertEquals(2, filtradas.size(), "deberia quedarse con la etiqueta y su valor");
    }

    @Test
    public void si_el_filtro_deja_cero_se_lee_todo() {
        // Es el caso que mas importa: un mapa que no corresponde a esta foto --el proveedor movio
        // el ticket, la foto salio corrida-- haria desaparecer TODOS los campos. Leer de mas es
        // lento; leer nada es inservible.
        List<MotorOcr.Zona> fueraDeLugar = Arrays.asList(zona(0.90, 0.90, 0.99, 0.99));

        List<DetectorCajas.Caja> filtradas = acotar(cupon(), fueraDeLugar, 400, 500);

        assertEquals(4, filtradas.size(), "ante un mapa desfasado hay que leer todo");
    }

    @Test
    public void el_margen_tolera_un_desvio_chico() {
        // La zona salio de OTRA foto del mismo modelo: la impresion se corre unos milimetros.
        // Esta zona no toca la caja del valor por poco, y el margen tiene que salvarla.
        // Caja del valor: x 80..180 (0.20..0.45), y 50..70 (0.10..0.14).
        List<MotorOcr.Zona> apenasArriba = Arrays.asList(zona(0.20, 0.07, 0.45, 0.095));

        List<DetectorCajas.Caja> filtradas = acotar(cupon(), apenasArriba, 400, 500);

        assertTrue(filtradas.size() < 4, "deberia filtrar algo");
        assertTrue(filtradas.size() >= 1, "el margen tendria que alcanzar la caja de al lado");
    }

    @Test
    public void una_caja_que_asoma_de_la_zona_igual_entra() {
        // Solapamiento, no contencion: el detector expande las cajas y una etiqueta larga puede
        // asomar del rectangulo derivado. Exigir contencion la dejaria afuera.
        List<MotorOcr.Zona> chica = Arrays.asList(zona(0.25, 0.11, 0.30, 0.13));

        List<DetectorCajas.Caja> filtradas = acotar(cupon(), chica, 400, 500);

        assertTrue(filtradas.size() >= 1, "la caja del valor se solapa con la zona");
    }

    @Test
    public void varias_zonas_suman() {
        List<MotorOcr.Zona> dos = Arrays.asList(
                zona(0.0, 0.10, 0.50, 0.14),   // el renglon del AUT
                zona(0.0, 0.79, 0.80, 0.85)    // el pie
        );

        List<DetectorCajas.Caja> filtradas = acotar(cupon(), dos, 400, 500);

        assertEquals(3, filtradas.size());
    }

    @Test
    public void una_caja_no_se_duplica_aunque_toque_dos_zonas() {
        List<MotorOcr.Zona> superpuestas = Arrays.asList(
                zona(0.0, 0.10, 0.50, 0.14),
                zona(0.1, 0.10, 0.60, 0.14)
        );

        List<DetectorCajas.Caja> filtradas = acotar(cupon(), superpuestas, 400, 500);

        assertEquals(2, filtradas.size(), "la misma caja no deberia entrar dos veces");
    }
}
