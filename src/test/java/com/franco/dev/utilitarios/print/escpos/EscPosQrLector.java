package com.franco.dev.utilitarios.print.escpos;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Para los tests: lee los QR que un ticket manda como imagen {@code ESC *} (filial #282). Arma cada bloque de
 * filas {@code ESC * m nL nH datos LF} seguidas como una imagen y la decodifica con zxing. Los bloques que no son
 * un QR (el logo) se ignoran.
 */
public final class EscPosQrLector {

    private EscPosQrLector() {
    }

    public static List<String> leer(byte[] b) {
        List<String> textos = new ArrayList<>();
        int i = 0;
        while (i < b.length) {
            if (!esFila(b, i)) {
                i++;
                continue;
            }
            List<boolean[][]> filas = new ArrayList<>();
            int ancho = 0;
            while (esFila(b, i)) {
                int m = b[i + 2] & 0xFF;
                ancho = (b[i + 3] & 0xFF) | ((b[i + 4] & 0xFF) << 8);
                int alto = (m == 32 || m == 33) ? 24 : 8;
                int bytesPorColumna = alto / 8;
                boolean[][] fila = new boolean[alto][ancho];
                int datos = i + 5;
                for (int x = 0; x < ancho; x++) {
                    for (int k = 0; k < bytesPorColumna; k++) {
                        int v = b[datos + x * bytesPorColumna + k] & 0xFF;
                        for (int bit = 0; bit < 8; bit++) {
                            fila[k * 8 + bit][x] = (v & (0x80 >> bit)) != 0;
                        }
                    }
                }
                filas.add(fila);
                i = datos + ancho * bytesPorColumna;
                if (i < b.length && b[i] == 0x0A) i++;
            }
            String texto = decodificar(filas, ancho);
            if (texto != null) textos.add(texto);
        }
        return textos;
    }

    private static boolean esFila(byte[] b, int i) {
        return i + 4 < b.length && b[i] == 0x1B && b[i + 1] == '*';
    }

    private static String decodificar(List<boolean[][]> filas, int ancho) {
        int alto = 0;
        for (boolean[][] f : filas) alto += f.length;
        if (ancho == 0 || alto == 0) return null;
        // Con margen blanco alrededor, como lo ve un lector sobre el papel.
        int margen = 16;
        BufferedImage img = new BufferedImage(ancho + 2 * margen, alto + 2 * margen, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, 0xFFFFFF);
        }
        int y0 = margen;
        for (boolean[][] f : filas) {
            for (int y = 0; y < f.length; y++) {
                for (int x = 0; x < ancho; x++) {
                    if (f[y][x]) img.setRGB(margen + x, y0 + y, 0x000000);
                }
            }
            y0 += f.length;
        }
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)));
            return new QRCodeReader().decode(bitmap).getText();
        } catch (Exception e) {
            return null;
        }
    }
}
