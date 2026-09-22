package com.franco.dev.service.financiero.ocr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fija la confianza por tramo, que es lo que convierte el numero del OCR en un semaforo por campo.
 *
 * <p>El caso que mas importa es el de alineacion: si los offsets que calcula
 * {@code confianzaEnRango} se corrieran aunque sea un caracter respecto de los que produce
 * {@code textoPorRenglones}, el semaforo mostraria la confianza <b>del campo de al lado</b> — y eso
 * es peor que no tener semaforo, porque el cajero confiaria en un dato malo.
 */
public class MotorOcrConfianzaTest {

    private static MotorOcr.Linea linea(String texto, float confianza,
                                        double x1, double y1, double x2, double y2) {
        double[][] caja = {{x1, y1}, {x2, y1}, {x2, y2}, {x1, y2}};
        return new MotorOcr.Linea(texto, confianza, caja);
    }

    /**
     * Un cupon de juguete. "AUT:" y su valor comparten renglon (se unen con espacio); "MONTO:" cae
     * en el de abajo (se une con salto). Los dos separadores ocupan un caracter, que es justamente
     * lo que hace que las posiciones no dependan de cual se eligio.
     */
    private static MotorOcr.Resultado cupon() {
        List<MotorOcr.Linea> l = new ArrayList<MotorOcr.Linea>(Arrays.asList(
                linea("AUT:",    0.99f, 10, 50,  60, 70),
                linea("883921",  0.80f, 80, 50, 180, 70),
                linea("MONTO:",  0.97f, 10, 90,  90, 110),
                linea("150",     0.40f, 110, 90, 160, 110),   // la mala
                linea(".000",    0.95f, 165, 90, 220, 110)
        ));
        return new MotorOcr.Resultado(l, 1, 1, 1, 1, 1);
    }

    @Test
    public void los_offsets_coinciden_con_el_texto_que_se_publica() {
        // EL test. Se busca el valor en el texto real y se pide la confianza de ESE tramo: si los
        // dos recorridos se desincronizan, esto devuelve la confianza de otra caja.
        MotorOcr.Resultado r = cupon();
        String texto = r.textoPorRenglones();

        int i = texto.indexOf("883921");
        assertTrue(i > 0, "el valor tiene que estar en el texto: " + texto);

        assertEquals(0.80f, r.confianzaEnRango(i, i + "883921".length()), 0.0001f);
    }

    @Test
    public void un_tramo_dentro_de_una_linea_devuelve_su_confianza() {
        MotorOcr.Resultado r = cupon();
        String texto = r.textoPorRenglones();
        int i = texto.indexOf("AUT:");

        assertEquals(0.99f, r.confianzaEnRango(i, i + 4), 0.0001f);
    }

    @Test
    public void un_valor_repartido_en_dos_cajas_devuelve_el_MINIMO() {
        // "150" se leyo mal y ".000" bien. Promediar daria 0.67 y el semaforo lo dejaria pasar;
        // pero el monto esta mal igual. Un caracter equivocado invalida el valor entero.
        MotorOcr.Resultado r = cupon();
        String texto = r.textoPorRenglones();
        int i = texto.indexOf("150");

        assertEquals(0.40f, r.confianzaEnRango(i, i + "150 .000".length()), 0.0001f);
    }

    @Test
    public void el_salto_de_renglon_ocupa_un_solo_caracter() {
        // Si el separador entre renglones contara distinto que el espacio, todo lo que viene
        // despues del primer salto quedaria corrido.
        MotorOcr.Resultado r = cupon();
        String texto = r.textoPorRenglones();

        assertEquals("AUT: 883921\nMONTO: 150 .000", texto);
        int i = texto.indexOf("MONTO:");
        assertEquals(0.97f, r.confianzaEnRango(i, i + 6), 0.0001f);
    }

    @Test
    public void un_tramo_que_no_toca_ninguna_linea_no_se_inventa() {
        // null = no se sabe. Quien lo consuma tiene que preguntar, no asumir que esta bien.
        MotorOcr.Resultado r = cupon();
        assertNull(r.confianzaEnRango(5000, 5010));
    }

    @Test
    public void un_rango_invalido_devuelve_null() {
        MotorOcr.Resultado r = cupon();
        assertNull(r.confianzaEnRango(-1, 5));
        assertNull(r.confianzaEnRango(10, 10));
        assertNull(r.confianzaEnRango(10, 3));
    }

    @Test
    public void sin_lineas_no_hay_confianza() {
        MotorOcr.Resultado vacio = new MotorOcr.Resultado(
                Collections.<MotorOcr.Linea>emptyList(), 1, 1, 1, 1, 1);
        assertNull(vacio.confianzaEnRango(0, 5));
    }

    @Test
    public void un_tramo_que_toca_apenas_el_borde_de_una_linea_la_cuenta() {
        // Solapamiento, no contencion: un patron puede capturar un caracter de mas.
        MotorOcr.Resultado r = cupon();
        String texto = r.textoPorRenglones();
        int i = texto.indexOf("883921");

        // Desde el espacio anterior: toca "AUT:" tambien, asi que gana el minimo de los dos.
        assertEquals(0.80f, r.confianzaEnRango(i - 1, i + 6), 0.0001f);
    }
}
