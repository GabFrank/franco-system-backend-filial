package com.franco.dev.service.financiero.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Deriva el mapa de un formato a partir de un cupon de muestra, sin que nadie dibuje nada.
 *
 * <p><b>Por que esto reemplaza al editor drag-and-drop.</b> Se evaluo usar IA para generar el
 * mapa; no hace falta. El OCR ya devuelve la geometria de cada linea ({@link MotorOcr.Linea#caja})
 * y el {@code patron} ya dice <b>que</b> es cada valor. Juntando las dos cosas la region sale por
 * construccion: se busca en que caja cayo cada grupo capturado, y esa caja es la region de ese
 * campo. Deterministico y auditable --si el patron matcheo, la region es correcta-- sin API key,
 * sin costo por llamada y sin alucinacion.
 *
 * <p><b>Lo que esto NO cubre</b>, y por eso la capa de IA sigue teniendo sentido: un proveedor
 * donde todavia no hay patron. Ahi hace falta que algo mire N cupones y lo proponga desde cero.
 *
 * <p><b>El ancla es la etiqueta, no la coordenada.</b> La geometria se devuelve igual, pero como
 * pista para acotar el reconocimiento. Lo que sobrevive a que el proveedor agregue una linea al
 * ticket es la etiqueta: si {@code AUT:} se corrio para abajo, el valor sigue a su derecha.
 *
 * <p><b>Propone, no persiste.</b> Las regiones viven en central, que es el publisher de
 * {@code MAIN_TO_ALL}. Este repo tiene el OCR y las fotos; central tiene el ABM. El filial deriva
 * y devuelve la propuesta, el administrador la revisa y central la guarda.
 */
@Slf4j
@Component
public class DerivadorMapa {

    /** Una region derivada, o el motivo por el que ese campo no se pudo derivar. */
    public static final class RegionPropuesta {
        public final String campo;
        public final String etiqueta;
        public final String posicion;
        public final String valorLeido;
        public final BigDecimal x1, y1, x2, y2;
        /** Null si se derivo bien. Si no, por que no se pudo. */
        public final String sinRegion;

        RegionPropuesta(String campo, String etiqueta, String posicion, String valorLeido,
                        BigDecimal x1, BigDecimal y1, BigDecimal x2, BigDecimal y2, String sinRegion) {
            this.campo = campo; this.etiqueta = etiqueta; this.posicion = posicion;
            this.valorLeido = valorLeido;
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.sinRegion = sinRegion;
        }

        public boolean derivada() { return sinRegion == null; }

        static RegionPropuesta noDerivada(String campo, String valor, String motivo) {
            return new RegionPropuesta(campo, null, null, valor, null, null, null, null, motivo);
        }
    }

    public static final class Resultado {
        public final List<RegionPropuesta> regiones;
        /** Null si salio bien. */
        public final String error;

        Resultado(List<RegionPropuesta> r, String e) { regiones = r; error = e; }

        public boolean ok() { return error == null; }

        /** Publico porque el servicio de captura tambien reporta fallos antes de llegar al OCR. */
        public static Resultado fallo(String e) {
            return new Resultado(new ArrayList<RegionPropuesta>(), e);
        }

        /** Cuantos campos quedaron sin region. Es lo que el administrador tiene que mirar. */
        public int sinRegion() {
            int n = 0;
            for (RegionPropuesta r : regiones) if (!r.derivada()) n++;
            return n;
        }
    }

    /**
     * Deriva el mapa.
     *
     * @param lineas lo que devolvio el OCR sobre el cupon de muestra
     * @param patron el regex con grupos nombrados del formato
     * @param ancho  ancho de la imagen en px, para normalizar
     * @param alto   alto de la imagen en px
     */
    public Resultado derivar(List<MotorOcr.Linea> lineas, String patron, int ancho, int alto) {
        if (lineas == null || lineas.isEmpty()) {
            return Resultado.fallo("el cupon de muestra no dejo ninguna linea leida");
        }
        if (patron == null || patron.trim().isEmpty()) {
            return Resultado.fallo("el formato no tiene patron; sin el no hay de donde saber que es cada valor");
        }
        if (ancho <= 0 || alto <= 0) {
            return Resultado.fallo("no se pudo determinar el tamano de la imagen");
        }

        String texto = unirPorRenglones(lineas);
        Matcher m;
        try {
            m = Pattern.compile(patron, Pattern.DOTALL).matcher(texto);
        } catch (PatternSyntaxException e) {
            return Resultado.fallo("el patron del formato no es valido");
        }
        if (!m.find()) {
            return Resultado.fallo("el patron no reconoce este cupon; probalo antes de derivar el mapa");
        }

        List<RegionPropuesta> out = new ArrayList<RegionPropuesta>();
        for (Map.Entry<String, String> g : capturas(patron, m).entrySet()) {
            out.add(derivarUno(g.getKey(), g.getValue(), lineas, ancho, alto));
        }
        if (out.isEmpty()) {
            return Resultado.fallo("el patron matcheo pero no tiene grupos nombrados que derivar");
        }
        return new Resultado(out, null);
    }

    /**
     * Una region: la caja que contiene el valor, mas la etiqueta que la ancla.
     *
     * <p><b>Si el valor no cae en UNA sola caja, no se inventa una region.</b> Puede pasar: el
     * detector separa por componentes conexos y un valor largo puede quedar repartido. Ese campo
     * queda sin region y cae al patron por texto, sin restriccion espacial. Un mapa parcial es
     * valido --mapa y patron conviven-- y es mucho mejor que una region mal dibujada, que despues
     * acota el reconocimiento y hace desaparecer un campo que hoy se lee bien.
     */
    private RegionPropuesta derivarUno(String campo, String valor, List<MotorOcr.Linea> lineas,
                                       int ancho, int alto) {
        List<MotorOcr.Linea> contienen = new ArrayList<MotorOcr.Linea>();
        for (MotorOcr.Linea l : lineas) {
            if (l.texto != null && l.texto.contains(valor)) contienen.add(l);
        }
        if (contienen.isEmpty()) {
            return RegionPropuesta.noDerivada(campo, valor,
                    "el valor quedo repartido entre varias cajas del OCR; este campo se resuelve por patron");
        }
        if (contienen.size() > 1) {
            return RegionPropuesta.noDerivada(campo, valor,
                    "el valor aparece en " + contienen.size() + " lugares del cupon; no se puede saber cual es");
        }

        MotorOcr.Linea caja = contienen.get(0);
        MotorOcr.Linea ancla = anclaDe(caja, lineas);

        String posicion;
        String etiqueta;
        if (ancla == null) {
            // Sin etiqueta a la vista. Se deriva igual con la geometria, pero el administrador
            // tiene que saber que este es el campo fragil ante un cambio de largo del ticket.
            posicion = "DENTRO";
            etiqueta = null;
        } else if (mismaFila(ancla, caja)) {
            posicion = "DERECHA";
            etiqueta = ancla.texto.trim();
        } else {
            posicion = "ABAJO";
            etiqueta = ancla.texto.trim();
        }

        // Si la etiqueta y el valor salieron en la MISMA caja, el ancla es el propio texto de la
        // caja menos el valor: "AUT: 883921" -> etiqueta "AUT:".
        if (ancla == null && caja.texto.trim().length() > valor.length()) {
            String sinValor = caja.texto.replace(valor, "").trim();
            if (!sinValor.isEmpty()) {
                etiqueta = sinValor;
                posicion = "DENTRO";
            }
        }

        return new RegionPropuesta(campo, etiqueta, posicion, valor,
                norm(minX(caja), ancho), norm(minY(caja), alto),
                norm(maxX(caja), ancho), norm(maxY(caja), alto), null);
    }

    /**
     * La caja que hace de etiqueta: la mas cercana a la izquierda en el mismo renglon; si no hay,
     * la que esta justo arriba.
     */
    private MotorOcr.Linea anclaDe(MotorOcr.Linea valor, List<MotorOcr.Linea> lineas) {
        MotorOcr.Linea mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (MotorOcr.Linea l : lineas) {
            if (l == valor) continue;
            if (!mismaFila(l, valor)) continue;
            if (maxX(l) > minX(valor)) continue;            // tiene que estar a la izquierda
            double d = minX(valor) - maxX(l);
            if (d < mejorDist) { mejorDist = d; mejor = l; }
        }
        if (mejor != null) return mejor;

        // Nadie a la izquierda: se prueba con el renglon de arriba, el mas cercano en X.
        for (MotorOcr.Linea l : lineas) {
            if (l == valor) continue;
            double dy = minY(valor) - maxY(l);
            if (dy <= 0 || dy > MotorOcr.TOLERANCIA_RENGLON * 4) continue;
            double d = Math.abs(minX(l) - minX(valor));
            if (d < mejorDist) { mejorDist = d; mejor = l; }
        }
        return mejor;
    }

    private static boolean mismaFila(MotorOcr.Linea a, MotorOcr.Linea b) {
        return Math.abs(minY(a) - minY(b)) < MotorOcr.TOLERANCIA_RENGLON;
    }

    /** Igual criterio que {@code Resultado.textoPorRenglones}: el patron corre sobre esto. */
    private static String unirPorRenglones(List<MotorOcr.Linea> lineas) {
        StringBuilder sb = new StringBuilder();
        double yAnterior = Double.NaN;
        for (MotorOcr.Linea l : lineas) {
            double y = minY(l);
            if (sb.length() > 0) {
                sb.append(Math.abs(y - yAnterior) < MotorOcr.TOLERANCIA_RENGLON ? ' ' : '\n');
            }
            sb.append(l.texto);
            yAnterior = y;
        }
        return sb.toString();
    }

    private Map<String, String> capturas(String patron, Matcher m) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Matcher nombres = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>").matcher(patron);
        while (nombres.find()) {
            String n = nombres.group(1);
            try {
                String v = m.group(n);
                if (v != null && !v.trim().isEmpty()) out.put(n, v.trim());
            } catch (IllegalArgumentException ignored) {
                // grupo declarado pero no presente en este match
            }
        }
        return out;
    }

    private static double minX(MotorOcr.Linea l) { return Math.min(Math.min(l.caja[0][0], l.caja[1][0]), Math.min(l.caja[2][0], l.caja[3][0])); }
    private static double maxX(MotorOcr.Linea l) { return Math.max(Math.max(l.caja[0][0], l.caja[1][0]), Math.max(l.caja[2][0], l.caja[3][0])); }
    private static double minY(MotorOcr.Linea l) { return Math.min(Math.min(l.caja[0][1], l.caja[1][1]), Math.min(l.caja[2][1], l.caja[3][1])); }
    private static double maxY(MotorOcr.Linea l) { return Math.max(Math.max(l.caja[0][1], l.caja[1][1]), Math.max(l.caja[2][1], l.caja[3][1])); }

    /** Normaliza a 0..1 y acota, porque el detector expande las cajas y puede pasarse del borde. */
    private static BigDecimal norm(double v, int total) {
        double r = v / total;
        if (r < 0) r = 0;
        if (r > 1) r = 1;
        return BigDecimal.valueOf(r).setScale(5, RoundingMode.HALF_UP);
    }
}
