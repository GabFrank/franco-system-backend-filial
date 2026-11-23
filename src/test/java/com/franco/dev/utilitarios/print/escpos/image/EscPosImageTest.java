package com.franco.dev.utilitarios.print.escpos.image;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cada franja de ESC * 33 lleva 3 bytes por columna, también la última. Si la imagen terminaba en una franja de
 * justo 8 o 16 filas, salía con 1 o 2 bytes por columna y la impresora tomaba lo que seguía como imagen (#282).
 */
class EscPosImageTest {

    private static List<ByteArrayOutputStream> filas(int ancho, int alto) {
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        return new EscPosImage(new CoffeeImageImpl(img), new BitonalThreshold()).getRasterRows(24);
    }

    @Test
    void todaFranjaDe24PuntosLlevaTresBytesPorColumna() {
        for (int alto = 1; alto <= 72; alto++) {
            for (ByteArrayOutputStream fila : filas(10, alto)) {
                assertEquals(30, fila.size(), "alto " + alto);
            }
        }
    }

    @Test
    void enModoDe8PuntosUnByteBastaPorColumna() {
        BufferedImage img = new BufferedImage(10, 16, BufferedImage.TYPE_INT_RGB);
        for (ByteArrayOutputStream fila : new EscPosImage(new CoffeeImageImpl(img), new BitonalThreshold()).getRasterRows(8)) {
            assertEquals(10, fila.size());
        }
    }
}
