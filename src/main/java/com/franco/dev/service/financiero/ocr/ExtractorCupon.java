package com.franco.dev.service.financiero.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.financiero.FormatoTerminalPos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Convierte el texto que devolvio el OCR en los campos de una venta con tarjeta.
 *
 * <p><b>Es la pieza que le faltaba al modulo.</b> Hasta esta entrega el OCR guardaba
 * {@code texto_ocr} y nada mas: {@code campos} nunca se llenaba, y ni {@code patron} ni
 * {@code mapeo} del formato tenian un solo consumidor. El cajero sacaba la foto, veia el texto en
 * pantalla y lo transcribia a mano igual — el OCR era una lupa, no un extractor.
 *
 * <p><b>Mismo vocabulario que el lector de QR del desktop</b> ({@code qr-pos-parser.ts}), y a
 * proposito: un cupon con QR y uno fotografiado tienen que producir los mismos campos, si no el
 * modulo tiene dos verdades. El vocabulario del {@code mapeo} es <b>cerrado</b> — {@code de},
 * {@code escala}, {@code mapa}, {@code mayusculas} — porque en cuanto se admiten expresiones el
 * ABM se convierte en un lenguaje de programacion dentro de un formulario y cualquiera puede
 * colgar una caja.
 *
 * <p><b>Lo que no cae en un campo canonico no se tira</b>: va a {@code datos_extra}. Eso es lo que
 * permite que un proveedor con campos propios --STONEID, COD.TRANS., lo que aparezca-- se resuelva
 * desde el ABM y no con una migracion.
 */
@Slf4j
@Component
public class ExtractorCupon {

    /**
     * Tope de la entrada, igual que {@code MAX_LONGITUD_QR} del desktop pero mas holgado porque un
     * ticket fotografiado es mas largo que una cadena de QR.
     *
     * <p>Java no tiene timeout de regex: contra un patron con backtracking patologico, acotar la
     * entrada es la unica defensa barata. Y el patron lo escribe un administrador, no el sistema.
     */
    public static final int MAX_LONGITUD_TEXTO = 4000;

    /** Los que tienen columna propia en {@code venta_tarjeta}. El resto cae en datos_extra. */
    private static final String[] CANONICOS = {
            "codigoAutorizacion", "numeroBoleta", "monto", "terminal", "identificadorTransaccion", "moneda"
    };

    private final ObjectMapper json = new ObjectMapper();

    /** Lo que se pudo leer del cupon. */
    public static final class Resultado {
        /** Campos canonicos, con el nombre que usa {@code venta_tarjeta}. */
        public final Map<String, Object> campos;
        /** Todo lo demas que el patron capturo. Va a {@code datos_extra}. */
        public final Map<String, Object> extras;
        /**
         * En que tramo {@code [inicio, fin)} del texto leido cayo cada campo canonico.
         *
         * <p>Existe para el semaforo por campo: el valor final ya paso por el mapeo --escala,
         * mapa, mayusculas-- asi que buscarlo de vuelta dentro del texto del OCR fallaria justo
         * en los campos transformados, que suelen ser los que mas importan (el monto). Los
         * offsets del match, en cambio, son exactos y no dependen de la transformacion.
         */
        public final Map<String, int[]> rangos;
        /** Null si salio bien. */
        public final String error;

        Resultado(Map<String, Object> c, Map<String, Object> e, Map<String, int[]> r, String err) {
            campos = c; extras = e; rangos = r; error = err;
        }

        public boolean ok() { return error == null; }

        static Resultado fallo(String e) {
            return new Resultado(Collections.<String, Object>emptyMap(),
                                 Collections.<String, Object>emptyMap(),
                                 Collections.<String, int[]>emptyMap(), e);
        }
    }

    /**
     * Aplica el patron y el mapeo del formato sobre el texto leido.
     *
     * <p>Nunca tira: un formato mal cargado o un cupon que no matchea son casos normales de
     * operacion, no errores de programa. El cajero siempre tiene la carga a mano como salida.
     */
    public Resultado extraer(String texto, FormatoTerminalPos formato) {
        if (texto == null || texto.trim().isEmpty()) {
            return Resultado.fallo("no se leyo texto en la foto");
        }
        if (texto.length() > MAX_LONGITUD_TEXTO) {
            return Resultado.fallo("el texto leido tiene " + texto.length()
                    + " caracteres y el maximo es " + MAX_LONGITUD_TEXTO);
        }
        if (formato == null) {
            return Resultado.fallo("la terminal no tiene formato configurado");
        }
        if (formato.getPatron() == null || formato.getPatron().trim().isEmpty()) {
            return Resultado.fallo("el formato \"" + formato.getNombre() + "\" no tiene patron cargado");
        }

        Matcher m;
        try {
            // DOTALL para que el patron pueda cruzar renglones con `.`; un ticket es multilinea
            // aunque la cadena de un QR no lo sea.
            m = Pattern.compile(formato.getPatron(), Pattern.DOTALL).matcher(texto);
        } catch (PatternSyntaxException e) {
            return Resultado.fallo("el patron del formato \"" + formato.getNombre() + "\" no es valido");
        }
        if (!m.find()) {
            return Resultado.fallo("el formato \"" + formato.getNombre() + "\" no reconocio el cupon");
        }

        JsonNode mapeo;
        try {
            mapeo = (formato.getMapeo() == null || formato.getMapeo().trim().isEmpty())
                    ? null : json.readTree(formato.getMapeo());
        } catch (Exception e) {
            return Resultado.fallo("el mapeo del formato \"" + formato.getNombre() + "\" no es un JSON valido");
        }

        Map<String, Object> campos = new LinkedHashMap<String, Object>();
        Map<String, Object> extras = new LinkedHashMap<String, Object>();
        // Solo de los canonicos: son los que el desktop confirma campo por campo. Lo que cae en
        // datos_extra no tiene formulario donde mostrar un semaforo.
        Map<String, int[]> rangos = new LinkedHashMap<String, int[]>();

        // Los grupos que alguna regla del mapeo ya consumio. Sin esto, un grupo `auth` mapeado a
        // `codigoAutorizacion` volveria a aparecer en datos_extra como "auth": el mismo valor
        // guardado dos veces con dos nombres. Lo encontro el test, no la lectura.
        Set<String> consumidos = new HashSet<String>();

        if (mapeo != null) {
            Iterator<String> it = mapeo.fieldNames();
            while (it.hasNext()) {
                String destino = it.next();
                JsonNode regla = mapeo.get(destino);
                if (regla != null && regla.hasNonNull("de")) consumidos.add(regla.get("de").asText());
                Object valor = aplicarRegla(m, regla);
                if (valor == null) continue;
                if (esCanonico(destino)) {
                    campos.put(destino, valor);
                    // El rango sale del grupo CRUDO, no del valor ya transformado.
                    if (regla.hasNonNull("de")) {
                        int[] r = rango(m, regla.get("de").asText());
                        if (r != null) rangos.put(destino, r);
                    }
                } else {
                    extras.put(destino, valor);
                }
            }
        }

        // Los grupos que el patron captura y el mapeo no menciona tambien sirven: son justamente
        // los campos propios del proveedor. Se toman con su nombre de grupo tal cual.
        //
        // Se marcan consumidos incluso cuando la regla no produjo valor --un campo opcional que
        // este cupon no trae--: el mapeo ya declaro que ese grupo tiene dueno, y volcarlo a
        // extras seria contradecirlo.
        for (Map.Entry<String, String> g : gruposNombrados(formato.getPatron(), m).entrySet()) {
            if (consumidos.contains(g.getKey())) continue;
            if (campos.containsKey(g.getKey()) || extras.containsKey(g.getKey())) continue;
            if (esCanonico(g.getKey())) {
                campos.put(g.getKey(), g.getValue());
                int[] r = rango(m, g.getKey());
                if (r != null) rangos.put(g.getKey(), r);
            } else {
                extras.put(g.getKey(), g.getValue());
            }
        }

        if (campos.isEmpty() && extras.isEmpty()) {
            return Resultado.fallo("el formato reconocio el cupon pero no se extrajo ningun campo");
        }
        return new Resultado(campos, extras, rangos, null);
    }

    /**
     * Donde empieza y termina un grupo dentro del texto sobre el que corrio el patron.
     *
     * <p>{@code null} si el grupo no existe en el patron o no participo del match — el mismo caso
     * que un campo opcional que este cupon no trae.
     */
    private static int[] rango(Matcher m, String grupo) {
        try {
            int inicio = m.start(grupo);
            int fin = m.end(grupo);
            return inicio >= 0 && fin > inicio ? new int[]{inicio, fin} : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Una regla del mapeo: de que grupo sale el valor y que transformacion se le aplica.
     *
     * <p>Devuelve {@code null} si el grupo no existe o vino vacio — es el caso de un campo
     * opcional que ese cupon no trae, no un error.
     */
    private Object aplicarRegla(Matcher m, JsonNode regla) {
        if (regla == null || !regla.hasNonNull("de")) return null;

        String crudo = grupo(m, regla.get("de").asText());
        if (crudo == null) return null;
        crudo = crudo.trim();
        if (crudo.isEmpty()) return null;

        // `mapa`: valor literal del cupon -> id nuestro. Es lo que resuelve la moneda.
        if (regla.has("mapa") && regla.get("mapa").hasNonNull(crudo)) {
            return regla.get("mapa").get(crudo).asLong();
        }

        if (regla.path("mayusculas").asBoolean(false)) {
            crudo = crudo.toUpperCase();
        }

        // `escala`: divisor fijo, para importes que vienen en la menor unidad.
        if (regla.has("escala")) {
            double escala = regla.get("escala").asDouble(0);
            if (escala > 0) {
                try {
                    return new BigDecimal(soloNumero(crudo))
                            .divide(BigDecimal.valueOf(escala), 4, RoundingMode.HALF_UP);
                } catch (NumberFormatException e) {
                    // El OCR leyo algo que no es un numero donde el mapeo esperaba uno. Se
                    // devuelve crudo para que el cajero lo vea y lo corrija, en vez de perderlo.
                    log.debug("el grupo {} no es numerico: {}", regla.get("de").asText(), crudo);
                    return crudo;
                }
            }
        }
        return crudo;
    }

    /** Todos los grupos nombrados del patron que hayan capturado algo. */
    private Map<String, String> gruposNombrados(String patron, Matcher m) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Matcher nombres = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>").matcher(patron);
        while (nombres.find()) {
            String n = nombres.group(1);
            String v = grupo(m, n);
            if (v != null && !v.trim().isEmpty()) out.put(n, v.trim());
        }
        return out;
    }

    /** {@code group(nombre)} tira si el grupo no existe en el patron; aca eso es null. */
    private String grupo(Matcher m, String nombre) {
        try {
            return m.group(nombre);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Deja solo lo que puede ser un numero. El OCR mete separadores de miles y, de vez en cuando,
     * un caracter que no existe en el papel.
     */
    private static String soloNumero(String s) {
        return s.replaceAll("[^0-9-]", "");
    }

    private static boolean esCanonico(String campo) {
        for (String c : CANONICOS) if (c.equals(campo)) return true;
        return false;
    }
}
