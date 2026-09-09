package com.franco.dev.service.financiero.ocr;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Imagen en BGR intercalado — el mismo orden de canales que entrega OpenCV,
 * que es lo que los modelos PP-OCR esperan. ImageIO entrega RGB, asi que la
 * conversion pasa aca una sola vez.
 */
public final class Imagen {
    public final int ancho, alto;
    public final byte[] px;          // BGR intercalado, longitud = ancho*alto*3

    public Imagen(int ancho, int alto) {
        this.ancho = ancho; this.alto = alto;
        this.px = new byte[ancho * alto * 3];
    }
    public Imagen(int ancho, int alto, byte[] px) {
        this.ancho = ancho; this.alto = alto; this.px = px;
    }

    public static Imagen leer(java.io.InputStream in) throws IOException {
        BufferedImage bi = ImageIO.read(in);
        if (bi == null) throw new IOException("no se pudo decodificar la imagen");
        return desde(bi);
    }

    public static Imagen leer(File f) throws IOException {
        BufferedImage bi = ImageIO.read(f);
        if (bi == null) throw new IOException("no se pudo leer la imagen: " + f);
        return desde(bi);
    }

    private static Imagen desde(BufferedImage bi) {
        Imagen im = new Imagen(bi.getWidth(), bi.getHeight());
        int k = 0;
        for (int y = 0; y < im.alto; y++)
            for (int x = 0; x < im.ancho; x++) {
                int rgb = bi.getRGB(x, y);
                im.px[k++] = (byte) (rgb & 0xFF);          // B
                im.px[k++] = (byte) ((rgb >> 8) & 0xFF);   // G
                im.px[k++] = (byte) ((rgb >> 16) & 0xFF);  // R
            }
        return im;
    }

    public int canal(int x, int y, int c) { return px[(y * ancho + x) * 3 + c] & 0xFF; }

    /**
     * Bilineal replicando cv2.resize(INTER_LINEAR) — no basta con la convencion
     * de coordenadas: OpenCV cuantiza los pesos a 1/2048 y acumula en enteros
     * con corrimiento de 22 bits. La diferencia contra aritmetica de doble
     * precision es de ~1 LSB por pixel, y en un recorte chico de texto termico
     * eso alcanza para cambiar un caracter.
     */
    public Imagen escalar(int nuevoAncho, int nuevoAlto) {
        final int BITS = 11, ESCALA = 1 << BITS;                 // INTER_RESIZE_COEF_BITS
        Imagen out = new Imagen(nuevoAncho, nuevoAlto);
        double ex = (double) ancho / nuevoAncho, ey = (double) alto / nuevoAlto;

        // tabla horizontal: indice de origen y los dos pesos, en punto fijo
        int[] xof = new int[nuevoAncho];
        int[] a0 = new int[nuevoAncho], a1 = new int[nuevoAncho];
        for (int dx = 0; dx < nuevoAncho; dx++) {
            float fx = (float) ((dx + 0.5) * ex - 0.5);
            int sx = (int) Math.floor(fx);
            fx -= sx;
            if (sx < 0)          { fx = 0; sx = 0; }
            if (sx >= ancho - 1) { fx = 0; sx = ancho - 1; }
            xof[dx] = sx;
            a1[dx] = Math.round(fx * ESCALA);
            a0[dx] = ESCALA - a1[dx];
        }

        for (int dy = 0; dy < nuevoAlto; dy++) {
            float fy = (float) ((dy + 0.5) * ey - 0.5);
            int sy = (int) Math.floor(fy);
            fy -= sy;
            if (sy < 0)         { fy = 0; sy = 0; }
            if (sy >= alto - 1) { fy = 0; sy = alto - 1; }
            int b1 = Math.round(fy * ESCALA), b0 = ESCALA - b1;
            int sy2 = Math.min(sy + 1, alto - 1);

            for (int dx = 0; dx < nuevoAncho; dx++) {
                int sx = xof[dx], sx2 = Math.min(sx + 1, ancho - 1);
                for (int c = 0; c < 3; c++) {
                    // pasada horizontal sobre las dos filas, luego vertical
                    int f0 = canal(sx, sy,  c) * a0[dx] + canal(sx2, sy,  c) * a1[dx];
                    int f1 = canal(sx, sy2, c) * a0[dx] + canal(sx2, sy2, c) * a1[dx];
                    long v = (long) f0 * b0 + (long) f1 * b1;
                    int r = (int) ((v + (1L << 21)) >> 22);      // BITS*2 = 22, con redondeo
                    out.px[(dy*nuevoAncho+dx)*3+c] = (byte) (r < 0 ? 0 : r > 255 ? 255 : r);
                }
            }
        }
        return out;
    }

    /** Rota 90 grados en sentido horario. */
    public Imagen rotar90() {
        Imagen out = new Imagen(alto, ancho);
        for (int y = 0; y < alto; y++)
            for (int x = 0; x < ancho; x++)
                for (int c = 0; c < 3; c++)
                    out.px[((x)*out.ancho + (alto-1-y))*3+c] = px[(y*ancho+x)*3+c];
        return out;
    }

    /** Rota 90 grados en sentido antihorario — equivale a np.rot90, que es
     *  lo que usa PP-OCR para enderezar recortes mas altos que anchos. */
    public Imagen rotar90Anti() {
        Imagen out = new Imagen(alto, ancho);
        for (int y = 0; y < alto; y++)
            for (int x = 0; x < ancho; x++)
                for (int c = 0; c < 3; c++)
                    out.px[((ancho-1-x)*out.ancho + y)*3+c] = px[(y*ancho+x)*3+c];
        return out;
    }

    /** Rota 180 grados. */
    public Imagen rotar180() {
        Imagen out = new Imagen(ancho, alto);
        int n = ancho * alto;
        for (int i = 0; i < n; i++)
            for (int c = 0; c < 3; c++)
                out.px[(n-1-i)*3+c] = px[i*3+c];
        return out;
    }

    /**
     * Recorta el cuadrilatero {p0..p3} y lo endereza — equivalente a
     * getPerspectiveTransform + warpPerspective de PP-OCR.
     * Se resuelve la homografia destino -> origen, que es el mapa que hace falta
     * para muestrear, evitando invertir una matriz despues.
     */
    public Imagen recortarCuadrilatero(double[][] p) {
        int w = (int) Math.round(Math.max(dist(p[0],p[1]), dist(p[2],p[3])));
        int h = (int) Math.round(Math.max(dist(p[0],p[3]), dist(p[1],p[2])));
        w = Math.max(w, 1); h = Math.max(h, 1);
        double[][] std = {{0,0},{w,0},{w,h},{0,h}};
        double[] H = homografia(std, p);          // destino -> origen

        Imagen out = new Imagen(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double den = H[6]*x + H[7]*y + 1.0;
                double u = (H[0]*x + H[1]*y + H[2]) / den;
                double v = (H[3]*x + H[4]*y + H[5]) / den;
                muestrear(out, x, y, u, v);
            }
        return out;
    }

    private void muestrear(Imagen out, int dx, int dy, double u, double v) {
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        double a = u - x0, b = v - y0;
        for (int c = 0; c < 3; c++) {
            double s = (1-a)*(1-b)*seguro(x0,y0,c) + a*(1-b)*seguro(x0+1,y0,c)
                     + (1-a)*b*seguro(x0,y0+1,c)  + a*b*seguro(x0+1,y0+1,c);
            out.px[(dy*out.ancho+dx)*3+c] = (byte) Math.max(0, Math.min(255, (int) Math.round(s)));
        }
    }
    private int seguro(int x, int y, int c) {
        x = Math.max(0, Math.min(ancho-1, x)); y = Math.max(0, Math.min(alto-1, y));
        return canal(x, y, c);
    }

    static double dist(double[] a, double[] b) { return Math.hypot(a[0]-b[0], a[1]-b[1]); }

    /** Homografia que lleva src -> dst. Devuelve [h0..h7] con h8 = 1. */
    static double[] homografia(double[][] src, double[][] dst) {
        double[][] A = new double[8][9];
        for (int i = 0; i < 4; i++) {
            double x = src[i][0], y = src[i][1], u = dst[i][0], v = dst[i][1];
            A[i*2]   = new double[]{x, y, 1, 0, 0, 0, -x*u, -y*u, u};
            A[i*2+1] = new double[]{0, 0, 0, x, y, 1, -x*v, -y*v, v};
        }
        // eliminacion gaussiana con pivoteo parcial
        for (int col = 0; col < 8; col++) {
            int piv = col;
            for (int r = col+1; r < 8; r++) if (Math.abs(A[r][col]) > Math.abs(A[piv][col])) piv = r;
            double[] t = A[col]; A[col] = A[piv]; A[piv] = t;
            double d = A[col][col];
            if (Math.abs(d) < 1e-12) continue;
            for (int j = col; j < 9; j++) A[col][j] /= d;
            for (int r = 0; r < 8; r++) {
                if (r == col) continue;
                double f = A[r][col];
                if (f == 0) continue;
                for (int j = col; j < 9; j++) A[r][j] -= f * A[col][j];
            }
        }
        double[] h = new double[8];
        for (int i = 0; i < 8; i++) h[i] = A[i][8];
        return h;
    }
}
