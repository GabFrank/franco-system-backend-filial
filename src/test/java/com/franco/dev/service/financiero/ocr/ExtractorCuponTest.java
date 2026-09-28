package com.franco.dev.service.financiero.ocr;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fija el comportamiento del extractor, que es la pieza que convierte el OCR en campos.
 *
 * <p>Los casos no son inventados: son los modos de falla que el modulo ya vio. Un cupon que no
 * matchea, un patron mal cargado, un OCR que leyo un caracter de mas en un importe, y un
 * proveedor con un campo propio que no tiene columna.
 */
public class ExtractorCuponTest {

    private final ExtractorCupon extractor = new ExtractorCupon();

    private static FormatoTerminalPos formato(String patron, String mapeo) {
        FormatoTerminalPos f = new FormatoTerminalPos();
        f.setNombre("PRUEBA");
        f.setPatron(patron);
        f.setMapeo(mapeo);
        return f;
    }

    @Test
    public void extrae_los_campos_canonicos_a_sus_columnas() {
        FormatoTerminalPos f = formato(
                ".*AUT: (?<auth>[0-9]+).*BOLETA: (?<boleta>[0-9]+).*MONTO: (?<monto>[0-9.]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"},"
                        + "\"numeroBoleta\":{\"de\":\"boleta\"},"
                        + "\"monto\":{\"de\":\"monto\"}}");

        ExtractorCupon.Resultado r = extractor.extraer(
                "COMERCIO X\nAUT: 883921\nBOLETA: 00045\nMONTO: 150.000", f);

        assertTrue(r.ok(), r.error);
        assertEquals("883921", r.campos.get("codigoAutorizacion"));
        assertEquals("00045", r.campos.get("numeroBoleta"));
        assertEquals("150.000", r.campos.get("monto"));
        assertTrue(r.extras.isEmpty(), "no deberia haber extras");
    }

    @Test
    public void un_campo_propio_DECLARADO_en_el_mapeo_es_un_campo_como_cualquier_otro() {
        // El caso que justifica datos_extra: un proveedor imprime un campo propio. Que no tenga
        // columna en venta_tarjeta no lo hace un dato de segunda: si el administrador se tomo el
        // trabajo de mapearlo, el cajero tiene que verlo, con semaforo y con chequeo de tipo.
        // Hasta el 2026-09-16 caia en extras y eso volvia decorativo su `obligatorio`.
        FormatoTerminalPos f = formato(
                ".*AUT: (?<auth>[0-9]+).*STONEID: (?<stoneId>[A-Z0-9]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"},\"stoneId\":{\"de\":\"stoneId\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("AUT: 12345\nSTONEID: XR44B", f);

        assertTrue(r.ok());
        assertEquals("12345", r.campos.get("codigoAutorizacion"));
        assertEquals("XR44B", r.campos.get("stoneId"));
        assertTrue(r.extras.isEmpty(), "lo declarado en el mapeo no es un extra");
        // Y lleva rango, que es lo que le da semaforo.
        assertTrue(r.rangos.containsKey("stoneId"));
    }

    @Test
    public void un_grupo_que_el_mapeo_no_menciona_igual_se_toma() {
        // Un formato cargado a medias no deberia perder datos que el patron ya captura.
        FormatoTerminalPos f = formato(".*AUT: (?<auth>[0-9]+).*LOTE: (?<lote>[0-9]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("AUT: 999\nLOTE: 77", f);

        assertTrue(r.ok());
        assertEquals("999", r.campos.get("codigoAutorizacion"));
        assertEquals("77", r.extras.get("lote"));
    }

    @Test
    public void escala_convierte_un_importe_en_la_menor_unidad() {
        FormatoTerminalPos f = formato(".*VAL(?<monto>[0-9]+).*",
                "{\"monto\":{\"de\":\"monto\",\"escala\":100}}");

        ExtractorCupon.Resultado r = extractor.extraer("VAL15000000", f);

        assertTrue(r.ok());
        assertEquals(0, new BigDecimal("150000.0000").compareTo((BigDecimal) r.campos.get("monto")));
    }

    @Test
    public void un_importe_que_el_ocr_ensucio_vuelve_crudo_en_vez_de_perderse() {
        // El OCR mete separadores y, de vez en cuando, un caracter que no esta en el papel.
        // Lo importante es que el cajero LO VEA para corregirlo, no que desaparezca.
        FormatoTerminalPos f = formato(".*VAL(?<monto>[0-9.O]+).*",
                "{\"monto\":{\"de\":\"monto\",\"escala\":100}}");

        ExtractorCupon.Resultado r = extractor.extraer("VAL150O00", f);

        assertTrue(r.ok());
        assertNotNull(r.campos.get("monto"));
    }

    @Test
    public void mapa_traduce_un_literal_del_cupon_a_un_id_nuestro() {
        FormatoTerminalPos f = formato(".*(?<mon>PYG|USD).*",
                "{\"moneda\":{\"de\":\"mon\",\"mapa\":{\"PYG\":1,\"USD\":3}}}");

        ExtractorCupon.Resultado r = extractor.extraer("TOTAL USD 30", f);

        assertTrue(r.ok());
        assertEquals(3L, r.campos.get("moneda"));
    }

    @Test
    public void mayusculas_normaliza() {
        FormatoTerminalPos f = formato(".*T:(?<t>[a-zA-Z0-9]+).*",
                "{\"terminal\":{\"de\":\"t\",\"mayusculas\":true}}");

        ExtractorCupon.Resultado r = extractor.extraer("T:jf798sjj", f);

        assertTrue(r.ok());
        assertEquals("JF798SJJ", r.campos.get("terminal"));
    }

    @Test
    public void un_cupon_que_no_matchea_no_tira_y_dice_por_que() {
        FormatoTerminalPos f = formato(".*AUT: (?<auth>[0-9]+).*", "{}");

        ExtractorCupon.Resultado r = extractor.extraer("ESTO ES OTRO TICKET", f);

        assertFalse(r.ok());
        assertTrue(r.error.contains("no reconocio"), r.error);
    }

    @Test
    public void un_patron_invalido_se_reporta_y_no_revienta() {
        FormatoTerminalPos f = formato("(?<sinCerrar", "{}");

        ExtractorCupon.Resultado r = extractor.extraer("lo que sea", f);

        assertFalse(r.ok());
        assertTrue(r.error.contains("no es valido"), r.error);
    }

    @Test
    public void un_mapeo_que_no_es_json_se_reporta_y_no_revienta() {
        FormatoTerminalPos f = formato(".*(?<a>.).*", "esto no es json");

        ExtractorCupon.Resultado r = extractor.extraer("x", f);

        assertFalse(r.ok());
        assertTrue(r.error.contains("JSON"), r.error);
    }

    @Test
    public void sin_formato_o_sin_patron_no_hay_extraccion() {
        assertFalse(extractor.extraer("AUT: 1", null).ok());
        assertFalse(extractor.extraer("AUT: 1", formato(null, "{}")).ok());
        assertFalse(extractor.extraer("AUT: 1", formato("   ", "{}")).ok());
    }

    @Test
    public void texto_vacio_o_gigante_se_rechaza_antes_de_tocar_el_regex() {
        FormatoTerminalPos f = formato(".*(?<a>.).*", "{}");

        assertFalse(extractor.extraer(null, f).ok());
        assertFalse(extractor.extraer("   ", f).ok());

        StringBuilder gigante = new StringBuilder();
        for (int i = 0; i < ExtractorCupon.MAX_LONGITUD_TEXTO + 1; i++) gigante.append('x');
        ExtractorCupon.Resultado r = extractor.extraer(gigante.toString(), f);
        assertFalse(r.ok());
        assertTrue(r.error.contains("maximo"), r.error);
    }

    @Test
    public void un_campo_opcional_que_el_cupon_no_trae_no_ensucia_la_salida() {
        FormatoTerminalPos f = formato(".*AUT: (?<auth>[0-9]+)(?<propina>[0-9]*)?.*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"},\"propina\":{\"de\":\"propina\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("AUT: 555", f);

        assertTrue(r.ok());
        assertEquals("555", r.campos.get("codigoAutorizacion"));
        assertFalse(r.extras.containsKey("propina"), "un grupo vacio no deberia aparecer");
    }

    @Test
    public void el_patron_cruza_renglones() {
        // DOTALL: un ticket es multilinea aunque la cadena de un QR no lo sea.
        FormatoTerminalPos f = formato("AUT: (?<auth>[0-9]+).*MONTO: (?<monto>[0-9]+)",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"},\"monto\":{\"de\":\"monto\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("AUT: 42\nLINEA DE RELLENO\nMONTO: 900", f);

        assertTrue(r.ok(), r.error);
        assertEquals("42", r.campos.get("codigoAutorizacion"));
        assertEquals("900", r.campos.get("monto"));
    }

    @Test
    public void el_orden_de_los_campos_es_estable() {
        // LinkedHashMap y no HashMap: el JSON que se guarda en `campos` no deberia cambiar de
        // orden entre corridas, o cualquier diff de soporte se vuelve ruido.
        FormatoTerminalPos f = formato(".*A(?<a>[0-9])B(?<b>[0-9])C(?<c>[0-9]).*",
                "{\"codigoAutorizacion\":{\"de\":\"a\"},\"numeroBoleta\":{\"de\":\"b\"},\"monto\":{\"de\":\"c\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("A1B2C3", f);

        assertTrue(r.ok());
        StringBuilder claves = new StringBuilder();
        for (Map.Entry<String, Object> e : r.campos.entrySet()) claves.append(e.getKey()).append(',');
        assertEquals("codigoAutorizacion,numeroBoleta,monto,", claves.toString());
    }

    // ── Rangos: de donde salio cada campo, para el semaforo por confianza ────────────────────

    @Test
    public void el_rango_apunta_al_grupo_CRUDO_aunque_el_mapeo_transforme_el_valor() {
        // EL caso que justifica guardar offsets en vez de buscar el valor final dentro del texto.
        // `escala` convierte "150.000" en 150000: buscar 150000 en el texto del OCR no lo
        // encuentra, y el monto --el campo que mas importa-- se quedaria sin semaforo.
        FormatoTerminalPos f = formato(
                ".*MONTO: (?<monto>[0-9.]+).*",
                "{\"monto\":{\"de\":\"monto\",\"escala\":0}}");
        String texto = "COMERCIO X\nMONTO: 150.000";

        ExtractorCupon.Resultado r = extractor.extraer(texto, f);

        assertTrue(r.ok(), r.error);
        int[] rango = r.rangos.get("monto");
        assertNotNull(rango, "el monto tiene que traer rango");
        assertEquals("150.000", texto.substring(rango[0], rango[1]),
                "el rango tiene que senalar el texto tal como se leyo");
    }

    @Test
    public void cada_campo_canonico_trae_su_propio_rango() {
        FormatoTerminalPos f = formato(
                ".*AUT: (?<auth>[0-9]+).*MONTO: (?<monto>[0-9.]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"},\"monto\":{\"de\":\"monto\"}}");
        String texto = "AUT: 883921\nMONTO: 150.000";

        ExtractorCupon.Resultado r = extractor.extraer(texto, f);

        assertEquals("883921", texto.substring(r.rangos.get("codigoAutorizacion")[0],
                                               r.rangos.get("codigoAutorizacion")[1]));
        assertEquals("150.000", texto.substring(r.rangos.get("monto")[0],
                                                r.rangos.get("monto")[1]));
    }

    @Test
    public void un_campo_canonico_sin_mapeo_tambien_trae_rango() {
        // El grupo se llama igual que la columna, asi que el mapeo no lo menciona.
        FormatoTerminalPos f = formato(
                ".*T: (?<terminal>[A-Z0-9]+).*AUT: (?<auth>[0-9]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"}}");
        String texto = "T: JF798SJJ\nAUT: 883921";

        ExtractorCupon.Resultado r = extractor.extraer(texto, f);

        assertEquals("JF798SJJ", texto.substring(r.rangos.get("terminal")[0],
                                                 r.rangos.get("terminal")[1]));
    }

    @Test
    public void los_extras_no_llevan_rango() {
        // Un grupo que el patron captura y el mapeo NO menciona sigue yendo a extras, y sin rango:
        // no hay declaracion a la que hacerle caso, asi que tampoco hay campo que mostrar ni
        // semaforo que calcular. Es la distincion que sobrevive al cambio del 2026-09-16.
        FormatoTerminalPos f = formato(
                ".*STONEID: (?<stoneId>[A-Z0-9]+).*AUT: (?<auth>[0-9]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("STONEID: XR44B\nAUT: 12345", f);

        assertEquals("XR44B", r.extras.get("stoneId"));
        assertFalse(r.rangos.containsKey("stoneId"));
        assertTrue(r.rangos.containsKey("codigoAutorizacion"));
    }

    @Test
    public void la_fecha_se_normaliza_a_iso_con_la_hora_de_otro_grupo() {
        // INFONET imprime F:02/09/2026H:22:51:34 -- fecha y hora con texto en el medio, por eso la
        // hora se nombra aparte. Sin esto el desktop recibia `fecha` vacia y `cuponVencido`
        // comparaba contra undefined: el control de 24 horas no podia dispararse nunca por el
        // camino del OCR.
        FormatoTerminalPos f = formato(
                ".*F:(?<fecha>[0-9/]+)H:(?<hora>[0-9:]+).*AUT: (?<auth>[0-9]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"},"
                        + "\"fecha\":{\"de\":\"fecha\",\"deHora\":\"hora\",\"formato\":\"dd/MM/yyyy\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("F:02/09/2026H:22:51:34\nAUT: 12345", f);

        assertTrue(r.ok(), r.error);
        assertEquals("2026-09-02T22:51:34", r.campos.get("fecha"));
    }

    @Test
    public void el_grupo_de_la_hora_no_se_guarda_ademas_como_extra() {
        // La hora ya vive adentro de `fecha`. Dejarla suelta en datos_extra seria el mismo dato
        // dos veces con dos nombres, que es lo que `consumidos` existe para evitar.
        FormatoTerminalPos f = formato(
                ".*F:(?<fecha>[0-9/]+)H:(?<hora>[0-9:]+).*AUT: (?<auth>[0-9]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"},"
                        + "\"fecha\":{\"de\":\"fecha\",\"deHora\":\"hora\",\"formato\":\"dd/MM/yyyy\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("F:02/09/2026H:22:51:34\nAUT: 12345", f);

        assertEquals("2026-09-02T22:51:34", r.campos.get("fecha"));
        assertTrue(r.extras.isEmpty(), "la hora no va suelta a datos_extra: " + r.extras);
    }

    @Test
    public void sin_hora_la_fecha_queda_a_medianoche() {
        // Conservador a proposito: adelanta el vencimiento hasta un dia, o sea avisa de mas.
        assertEquals("2026-09-02T00:00", ExtractorCupon.fechaIso("02/09/2026", "dd/MM/yyyy", null));
    }

    @Test
    public void una_fecha_que_no_existe_no_se_desborda_al_mes_siguiente() {
        assertNull(ExtractorCupon.fechaIso("31/02/2026", "dd/MM/yyyy", null));
    }

    @Test
    public void una_fecha_ilegible_devuelve_el_valor_crudo_en_vez_de_perderlo() {
        // Mismo criterio que `escala`: mostrarlo sin normalizar es mejor que tirarlo. El control
        // de antiguedad simplemente no corre, que es como venia funcionando.
        FormatoTerminalPos f = formato(
                ".*F:(?<fecha>[^\\n]+).*AUT: (?<auth>[0-9]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"},"
                        + "\"fecha\":{\"de\":\"fecha\",\"formato\":\"dd/MM/yyyy\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("F:O2/O9/2O26\nAUT: 12345", f);

        assertTrue(r.ok(), r.error);
        assertEquals("O2/O9/2O26", r.campos.get("fecha"));
    }

    @Test
    public void un_fallo_devuelve_rangos_vacios_y_no_null() {
        ExtractorCupon.Resultado r = extractor.extraer("cualquier cosa", formato("^NADA$", "{}"));

        assertFalse(r.ok());
        assertNotNull(r.rangos);
        assertTrue(r.rangos.isEmpty());
    }

    // ---------------------------------------------------------------------------------------
    // Rescate parcial: el patron entero no matchea, pero lo que se leyo bien no se tira.
    // ---------------------------------------------------------------------------------------

    /** El patron real de INFONET POS, tal cual esta cargado en el ABM. */
    private static final String PATRON_INFONET =
            "^[\\s\\S]*C\\.N\\.:\\s*(?<cn>[A-Z0-9]+)\\s*"
            + "(?:F:\\s*(?<fecha>\\d{2}/\\d{2}/\\d{4})\\s*H:\\s*(?<hora>\\d{2}:\\d{2}:\\d{2}))?"
            + "[\\s\\S]*BOLETA:\\s*(?<boleta>[0-9]+)"
            + "[\\s\\S]*C\\.AUT:\\s*(?<auth>[A-Z0-9]+)"
            + "[\\s\\S]*G\\.\\s*(?<monto>[0-9][0-9.]*)"
            + "(?:[\\s\\S]*Lote:\\s*(?<lote>[0-9]+))?[\\s\\S]*$";

    private static final String MAPEO_INFONET =
            "{\"terminal\":{\"de\":\"cn\"},"
            + "\"numeroBoleta\":{\"de\":\"boleta\",\"obligatorio\":true},"
            + "\"codigoAutorizacion\":{\"de\":\"auth\",\"obligatorio\":true},"
            + "\"monto\":{\"de\":\"monto\",\"obligatorio\":true},"
            + "\"lote\":{\"de\":\"lote\"}}";

    /**
     * El texto que devolvio el OCR de la captura 29 del 2026-09-17, copiado tal cual de la base.
     * Le falta el renglon del monto: es exactamente lo que hizo fallar el patron entero.
     */
    private static final String OCR_SIN_MONTO =
            "INFNET\n"
            + "AUTOSERV.FRANCO-30 DE JULIO\n"
            + "30 DE JULIO C.AV.PARAGUAY\n"
            + "C.N.:82829 F:02/09/2026H:20:47:29\n"
            + "BOLETA:5671193576\n"
            + "QR DEB MC CLA BANCO FAMILIAR\n"
            + "92206-05112 V2.2160\n"
            + "C.AUT:193576";

    @Test
    public void un_campo_ilegible_ya_no_se_lleva_puestos_a_los_demas() {
        // El caso medido el 2026-09-17: al OCR se le escapo el renglon del monto y, como el patron
        // es una sola expresion, `campos` quedaba vacio. El cajero tenia que tipear los cuatro
        // campos aunque tres estuvieran perfectamente leidos. Tres de las ultimas seis capturas
        // de esa jornada terminaron asi.
        ExtractorCupon.Resultado r = extractor.extraer(OCR_SIN_MONTO,
                formato(PATRON_INFONET, MAPEO_INFONET));

        assertTrue(r.ok(), r.error);
        assertTrue(r.parcial, "tiene que quedar marcado como parcial");
        assertEquals("5671193576", r.campos.get("numeroBoleta"));
        assertEquals("193576", r.campos.get("codigoAutorizacion"));
        assertEquals("82829", r.campos.get("terminal"));
        assertNull(r.campos.get("monto"), "el monto no estaba en el cupon leido");
    }

    @Test
    public void el_tramo_del_monto_sobrevive_aunque_arrastre_un_grupo_opcional() {
        // El corte respeta parentesis. El tramo del monto termina en `(?:[\s\S]*Lote:...)?`, asi
        // que cortar por cada `[\s\S]*` sin mirar profundidad partia ese grupo al medio y dejaba
        // dos pedazos invalidos -- perdiendo justo el campo mas importante.
        String sinBoleta =
                "INFNET\nC.N.:82829 F:02/09/2026H:20:47:29\nC.AUT:193576\nG. 3.500\nLote: 1199";

        ExtractorCupon.Resultado r = extractor.extraer(sinBoleta,
                formato(PATRON_INFONET, MAPEO_INFONET));

        assertTrue(r.ok(), r.error);
        assertTrue(r.parcial);
        assertEquals("3.500", r.campos.get("monto"));
        assertEquals("1199", r.campos.get("lote"));
        assertNull(r.campos.get("numeroBoleta"));
    }

    @Test
    public void un_cupon_completo_NO_se_marca_parcial() {
        String completo =
                "INFNET\nC.N.:82829 F:02/09/2026H:20:47:29\nBOLETA:5671193576\nC.AUT:193576\n"
                + "G. 3.500\nLote: 1199";

        ExtractorCupon.Resultado r = extractor.extraer(completo,
                formato(PATRON_INFONET, MAPEO_INFONET));

        assertTrue(r.ok(), r.error);
        assertFalse(r.parcial, "matcheo entero: no es parcial");
        assertEquals("3.500", r.campos.get("monto"));
        assertEquals("5671193576", r.campos.get("numeroBoleta"));
    }

    @Test
    public void un_texto_que_no_tiene_nada_del_formato_sigue_fallando() {
        // El rescate parcial no puede convertir un fallo legitimo en un exito a medias: si no se
        // reconocio NINGUN tramo, la respuesta sigue siendo que el formato no reconocio el cupon.
        ExtractorCupon.Resultado r = extractor.extraer("TICKET DE ESTACIONAMIENTO\n0800-1234",
                formato(PATRON_INFONET, MAPEO_INFONET));

        assertFalse(r.ok());
        assertTrue(r.error.contains("no reconocio el cupon"), r.error);
    }

    @Test
    public void los_rangos_del_rescate_parcial_son_offsets_del_texto_completo() {
        // El semaforo por campo se calcula con estos offsets. Si cada tramo devolviera posiciones
        // relativas a si mismo, la confianza se leeria del pedazo equivocado del OCR.
        ExtractorCupon.Resultado r = extractor.extraer(OCR_SIN_MONTO,
                formato(PATRON_INFONET, MAPEO_INFONET));

        int[] rangoBoleta = r.rangos.get("numeroBoleta");
        assertNotNull(rangoBoleta, "la boleta tiene que traer rango");
        assertEquals("5671193576",
                OCR_SIN_MONTO.substring(rangoBoleta[0], rangoBoleta[1]));
    }

    @Test
    public void un_patron_patologico_falla_por_plazo_en_vez_de_colgar() {
        // `(a+)+b` sobre muchas `a` sin ninguna `b` es el backtracking catastrofico de manual:
        // exponencial en el largo. Sin el plazo, 4.000 caracteres alcanzan para colgar el hilo
        // por horas --con la fila de captura_cupon bajo lock--. Un administrador puede escribir
        // ese patron sin querer, y el guardado no lo detecta: matchea su ejemplo corto al instante.
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < 4000; i++) texto.append('a');
        FormatoTerminalPos f = formato("(?<x>(a+)+b)", "{\"numeroBoleta\":{\"de\":\"x\"}}");

        long t0 = System.nanoTime();
        ExtractorCupon.Resultado r = extractor.extraer(texto.toString(), f);
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        assertFalse(r.ok(), "tenia que fallar, no matchear");
        assertTrue(r.error.contains("tardo mas de"), "el error tiene que decir que fue por plazo: " + r.error);
        assertTrue(ms < 2000, "tardo " + ms + " ms: el plazo de " + ExtractorCupon.PLAZO_MS + " ms no corto");
    }

    @Test
    public void el_plazo_no_afecta_a_un_patron_normal() {
        // Guardia contra un plazo mal puesto: el mismo texto de siempre tiene que seguir saliendo
        // completo, con rangos, por el camino normal.
        ExtractorCupon.Resultado r = extractor.extraer(
                "COMERCIO X\nAUT: 883921\nBOLETA: 00045\nMONTO: 150.000",
                formato(".*AUT: (?<auth>[0-9]+).*BOLETA: (?<boleta>[0-9]+).*MONTO: (?<monto>[0-9.]+).*",
                        "{\"codigoAutorizacion\":{\"de\":\"auth\"},"
                                + "\"numeroBoleta\":{\"de\":\"boleta\"},"
                                + "\"monto\":{\"de\":\"monto\"}}"));
        assertTrue(r.ok(), r.error);
        assertFalse(r.parcial);
        assertEquals("00045", r.campos.get("numeroBoleta"));
    }
}
