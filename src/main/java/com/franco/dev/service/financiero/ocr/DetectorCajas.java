package com.franco.dev.service.financiero.ocr;

import java.util.*;

/**
 * Post-proceso DB (Differentiable Binarization) en Java puro.
 * Reemplaza a cv2.dilate + findContours + minAreaRect + fillPoly + pyclipper,
 * que es lo que hace RapidOCR. Sin OpenCV: nada nativo que distribuir.
 */
public final class DetectorCajas {

    public static final class Caja {
        public final double[][] p;    // 4 puntos: arribaIzq, arribaDer, abajoDer, abajoIzq
        public final double puntaje;
        Caja(double[][] p, double puntaje) { this.p = p; this.puntaje = puntaje; }
    }

    private final double umbral, umbralCaja, razonExpansion;
    private final int maxCandidatas, tamMin;
    private final boolean dilatar;

    public DetectorCajas(double umbral, double umbralCaja, double razonExpansion,
                         int maxCandidatas, boolean dilatar) {
        this.umbral = umbral; this.umbralCaja = umbralCaja;
        this.razonExpansion = razonExpansion; this.maxCandidatas = maxCandidatas;
        this.dilatar = dilatar; this.tamMin = 3;
    }

    /** pred: mapa de probabilidad [alto][ancho] a la escala de la red. */
    public List<Caja> cajas(float[] pred, int ancho, int alto, int anchoDest, int altoDest) {
        boolean[] bin = new boolean[ancho * alto];
        for (int i = 0; i < bin.length; i++) bin[i] = pred[i] > umbral;

        boolean[] mask = dilatar ? dilatar2x2(bin, ancho, alto) : bin;

        List<Caja> salida = new ArrayList<>();
        int[] etiqueta = new int[ancho * alto];
        int[] pila = new int[ancho * alto];
        int comp = 0;

        for (int inicio = 0; inicio < mask.length && salida.size() < maxCandidatas; inicio++) {
            if (!mask[inicio] || etiqueta[inicio] != 0) continue;
            comp++;
            // relleno por inundacion, 8-conectividad, iterativo (la recursion desborda la pila)
            int tope = 0, n = 0;
            pila[tope++] = inicio; etiqueta[inicio] = comp;
            int[] xs = new int[64], ys = new int[64];
            while (tope > 0) {
                int idx = pila[--tope];
                int x = idx % ancho, y = idx / ancho;
                if (n == xs.length) { xs = Arrays.copyOf(xs, n*2); ys = Arrays.copyOf(ys, n*2); }
                xs[n] = x; ys[n] = y; n++;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= ancho || ny >= alto) continue;
                        int ni = ny * ancho + nx;
                        if (mask[ni] && etiqueta[ni] == 0) { etiqueta[ni] = comp; pila[tope++] = ni; }
                    }
            }

            RectRotado r = rectMinimo(xs, ys, n);
            if (r == null || Math.min(r.w, r.h) < tamMin) continue;

            double[][] pts = ordenar(r.esquinas());
            double puntaje = puntajeRapido(pred, ancho, alto, pts);
            if (puntaje < umbralCaja) continue;

            // unclip: sobre un rectangulo, desplazar el poligono una distancia d
            // equivale exactamente a (w+2d) x (h+2d) con el mismo centro y angulo
            double area = r.w * r.h, perim = 2 * (r.w + r.h);
            if (perim <= 0) continue;
            double d = area * razonExpansion / perim;
            RectRotado exp = new RectRotado(r.cx, r.cy, r.w + 2*d, r.h + 2*d, r.ang);
            if (Math.min(exp.w, exp.h) < tamMin + 2) continue;

            // Nota: contra Python queda una diferencia de ~1 px por caja (IoU medio 0,974).
            // Verificado que NO viene del detector (salida identica bit a bit), ni de la
            // dilatacion (identica a cv2), ni del resize, ni de cuantizar a enteros aca:
            // es el redondeo sub-pixel de minAreaRect y de la aproximacion de arcos de
            // pyclipper. Cerrarlo exige replicar la aritmetica float32 de OpenCV.
            double[][] q = ordenar(exp.esquinas());
            for (double[] pt : q) {
                pt[0] = Math.max(0, Math.min(anchoDest, Math.round(pt[0] / ancho * anchoDest)));
                pt[1] = Math.max(0, Math.min(altoDest,  Math.round(pt[1] / alto  * altoDest)));
            }
            double ancho1 = Imagen.dist(q[0], q[1]), alto1 = Imagen.dist(q[0], q[3]);
            if ((int) ancho1 <= 3 || (int) alto1 <= 3) continue;
            salida.add(new Caja(q, puntaje));
        }
        return salida;
    }

    /** cv2.dilate con kernel 2x2: para tamano par el ancla queda en (1,1). */
    private static boolean[] dilatar2x2(boolean[] src, int w, int h) {
        boolean[] out = new boolean[src.length];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                boolean v = false;
                for (int dy = -1; dy <= 0 && !v; dy++)
                    for (int dx = -1; dx <= 0 && !v; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && ny >= 0 && src[ny*w + nx]) v = true;
                    }
                out[y*w + x] = v;
            }
        return out;
    }

    /** Media de pred dentro del cuadrilatero — equivalente a fillPoly + cv2.mean. */
    private static double puntajeRapido(float[] pred, int w, int h, double[][] box) {
        double xmn = box[0][0], xmx = box[0][0], ymn = box[0][1], ymx = box[0][1];
        for (double[] p : box) {
            xmn = Math.min(xmn, p[0]); xmx = Math.max(xmx, p[0]);
            ymn = Math.min(ymn, p[1]); ymx = Math.max(ymx, p[1]);
        }
        int x0 = (int) Math.max(0, Math.min(w-1, Math.floor(xmn)));
        int x1 = (int) Math.max(0, Math.min(w-1, Math.ceil(xmx)));
        int y0 = (int) Math.max(0, Math.min(h-1, Math.floor(ymn)));
        int y1 = (int) Math.max(0, Math.min(h-1, Math.ceil(ymx)));

        double suma = 0; int cuenta = 0;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                if (dentro(box, x + 0.5, y + 0.5)) { suma += pred[y*w + x]; cuenta++; }
        return cuenta == 0 ? 0 : suma / cuenta;
    }

    private static boolean dentro(double[][] p, double x, double y) {
        boolean d = false;
        for (int i = 0, j = 3; i < 4; j = i++)
            if (((p[i][1] > y) != (p[j][1] > y)) &&
                (x < (p[j][0]-p[i][0]) * (y-p[i][1]) / (p[j][1]-p[i][1]) + p[i][0])) d = !d;
        return d;
    }

    /** Mismo orden que get_mini_boxes: ordenar por x, luego resolver por y. */
    static double[][] ordenar(double[][] pts) {
        double[][] s = pts.clone();
        Arrays.sort(s, Comparator.comparingDouble(a -> a[0]));
        int i1, i4, i2, i3;
        if (s[1][1] > s[0][1]) { i1 = 0; i4 = 1; } else { i1 = 1; i4 = 0; }
        if (s[3][1] > s[2][1]) { i2 = 2; i3 = 3; } else { i2 = 3; i3 = 2; }
        return new double[][]{
            {s[i1][0], s[i1][1]}, {s[i2][0], s[i2][1]},
            {s[i3][0], s[i3][1]}, {s[i4][0], s[i4][1]}};
    }

    // ---------- rectangulo de area minima ----------

    static final class RectRotado {
        final double cx, cy, w, h, ang;
        RectRotado(double cx, double cy, double w, double h, double ang) {
            this.cx=cx; this.cy=cy; this.w=w; this.h=h; this.ang=ang;
        }
        double[][] esquinas() {
            double c = Math.cos(ang), s = Math.sin(ang), hw = w/2, hh = h/2;
            double[][] o = new double[4][2];
            double[][] loc = {{-hw,-hh},{hw,-hh},{hw,hh},{-hw,hh}};
            for (int i = 0; i < 4; i++) {
                o[i][0] = cx + loc[i][0]*c - loc[i][1]*s;
                o[i][1] = cy + loc[i][0]*s + loc[i][1]*c;
            }
            return o;
        }
    }

    /** Calipers rotantes sobre la envolvente convexa. */
    static RectRotado rectMinimo(int[] xs, int[] ys, int n) {
        double[][] hull = envolvente(xs, ys, n);
        if (hull.length < 2) return null;
        double mejorArea = Double.MAX_VALUE; RectRotado mejor = null;
        for (int i = 0; i < hull.length; i++) {
            double[] a = hull[i], b = hull[(i+1) % hull.length];
            double ex = b[0]-a[0], ey = b[1]-a[1];
            double L = Math.hypot(ex, ey);
            if (L < 1e-9) continue;
            double ux = ex/L, uy = ey/L, nx = -uy, ny = ux;
            double mnU=Double.MAX_VALUE, mxU=-Double.MAX_VALUE, mnV=Double.MAX_VALUE, mxV=-Double.MAX_VALUE;
            for (double[] p : hull) {
                double u = p[0]*ux + p[1]*uy, v = p[0]*nx + p[1]*ny;
                mnU=Math.min(mnU,u); mxU=Math.max(mxU,u);
                mnV=Math.min(mnV,v); mxV=Math.max(mxV,v);
            }
            double w = mxU-mnU, h = mxV-mnV, area = w*h;
            if (area < mejorArea) {
                mejorArea = area;
                double cu = (mnU+mxU)/2, cv = (mnV+mxV)/2;
                mejor = new RectRotado(cu*ux + cv*nx, cu*uy + cv*ny, w, h, Math.atan2(uy, ux));
            }
        }
        return mejor;
    }

    /** Envolvente convexa por cadena monotona de Andrew. */
    static double[][] envolvente(int[] xs, int[] ys, int n) {
        double[][] p = new double[n][2];
        for (int i = 0; i < n; i++) { p[i][0] = xs[i]; p[i][1] = ys[i]; }
        Arrays.sort(p, (a,b) -> a[0]!=b[0] ? Double.compare(a[0],b[0]) : Double.compare(a[1],b[1]));
        double[][] h = new double[2*n][];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cruz(h[k-2], h[k-1], p[i]) <= 0) k--;
            h[k++] = p[i];
        }
        for (int i = n-2, t = k+1; i >= 0; i--) {
            while (k >= t && cruz(h[k-2], h[k-1], p[i]) <= 0) k--;
            h[k++] = p[i];
        }
        return Arrays.copyOf(h, Math.max(k-1, 1));
    }
    static double cruz(double[] o, double[] a, double[] b) {
        return (a[0]-o[0])*(b[1]-o[1]) - (a[1]-o[1])*(b[0]-o[0]);
    }
}
