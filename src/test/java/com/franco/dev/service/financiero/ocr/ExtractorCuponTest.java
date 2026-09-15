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
    public void lo_que_no_es_canonico_va_a_extras_sin_migracion() {
        // El caso que justifica datos_extra: un proveedor imprime un campo propio.
        FormatoTerminalPos f = formato(
                ".*AUT: (?<auth>[0-9]+).*STONEID: (?<stoneId>[A-Z0-9]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"},\"stoneId\":{\"de\":\"stoneId\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("AUT: 12345\nSTONEID: XR44B", f);

        assertTrue(r.ok());
        assertEquals("12345", r.campos.get("codigoAutorizacion"));
        assertEquals("XR44B", r.extras.get("stoneId"));
        assertFalse(r.campos.containsKey("stoneId"), "stoneId no tiene columna propia");
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
        // datos_extra no tiene formulario donde mostrar un semaforo; calcularlo seria trabajo
        // que nadie consume.
        FormatoTerminalPos f = formato(
                ".*STONEID: (?<stoneId>[A-Z0-9]+).*AUT: (?<auth>[0-9]+).*",
                "{\"codigoAutorizacion\":{\"de\":\"auth\"},\"stoneId\":{\"de\":\"stoneId\"}}");

        ExtractorCupon.Resultado r = extractor.extraer("STONEID: XR44B\nAUT: 12345", f);

        assertFalse(r.rangos.containsKey("stoneId"));
        assertTrue(r.rangos.containsKey("codigoAutorizacion"));
    }

    @Test
    public void un_fallo_devuelve_rangos_vacios_y_no_null() {
        ExtractorCupon.Resultado r = extractor.extraer("cualquier cosa", formato("^NADA$", "{}"));

        assertFalse(r.ok());
        assertNotNull(r.rangos);
        assertTrue(r.rangos.isEmpty());
    }
}
