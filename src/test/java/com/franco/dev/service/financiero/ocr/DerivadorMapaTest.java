package com.franco.dev.service.financiero.ocr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fija el comportamiento de la derivacion del mapa, que es lo que reemplaza al editor
 * drag-and-drop.
 *
 * <p>El caso que mas importa es el negativo: un valor que el OCR partio en dos cajas <b>no</b>
 * debe producir una region. Una region mal dibujada es peor que ninguna, porque despues acota el
 * reconocimiento y hace desaparecer un campo que hoy se lee bien.
 */
public class DerivadorMapaTest {

    private final DerivadorMapa derivador = new DerivadorMapa();

    /** Una caja rectangular, en el orden de puntos que usa el detector. */
    private static MotorOcr.Linea linea(String texto, double x1, double y1, double x2, double y2) {
        double[][] caja = {{x1, y1}, {x2, y1}, {x2, y2}, {x1, y2}};
        return new MotorOcr.Linea(texto, 0.95f, caja);
    }

    /** Un cupon de juguete: etiqueta y valor en cajas separadas, como sale del detector real. */
    private static List<MotorOcr.Linea> cupon() {
        return new ArrayList<MotorOcr.Linea>(Arrays.asList(
                linea("COMERCIO DEMO", 10, 10, 300, 30),
                linea("AUT:", 10, 50, 60, 70),
                linea("883921", 80, 50, 180, 70),
                linea("MONTO:", 10, 90, 90, 110),
                linea("150.000", 110, 90, 220, 110)
        ));
    }

    @Test
    public void deriva_la_region_del_valor_y_la_ancla_a_su_etiqueta() {
        DerivadorMapa.Resultado r = derivador.derivar(cupon(),
                ".*AUT: (?<auth>[0-9]+).*", 400, 200);

        assertTrue(r.ok(), r.error);
        assertEquals(1, r.regiones.size());
        DerivadorMapa.RegionPropuesta auth = r.regiones.get(0);
        assertTrue(auth.derivada(), auth.sinRegion);
        assertEquals("auth", auth.campo);
        assertEquals("883921", auth.valorLeido);
        // La etiqueta es la caja de la izquierda en el mismo renglon.
        assertEquals("AUT:", auth.etiqueta);
        assertEquals("DERECHA", auth.posicion);
    }

    @Test
    public void la_geometria_viene_normalizada_a_cero_uno() {
        DerivadorMapa.Resultado r = derivador.derivar(cupon(),
                ".*AUT: (?<auth>[0-9]+).*", 400, 200);

        DerivadorMapa.RegionPropuesta auth = r.regiones.get(0);
        // La caja del valor es x 80..180 sobre 400 de ancho, y 50..70 sobre 200 de alto.
        assertEquals(0, auth.x1.compareTo(new java.math.BigDecimal("0.20000")), "x1=" + auth.x1);
        assertEquals(0, auth.x2.compareTo(new java.math.BigDecimal("0.45000")), "x2=" + auth.x2);
        assertEquals(0, auth.y1.compareTo(new java.math.BigDecimal("0.25000")), "y1=" + auth.y1);
        assertEquals(0, auth.y2.compareTo(new java.math.BigDecimal("0.35000")), "y2=" + auth.y2);
    }

    @Test
    public void deriva_varios_campos_de_una_pasada() {
        DerivadorMapa.Resultado r = derivador.derivar(cupon(),
                ".*AUT: (?<auth>[0-9]+).*MONTO: (?<monto>[0-9.]+).*", 400, 200);

        assertTrue(r.ok(), r.error);
        assertEquals(2, r.regiones.size());
        assertEquals(0, r.sinRegion());
        assertEquals("MONTO:", r.regiones.get(1).etiqueta);
    }

    @Test
    public void un_valor_repartido_en_dos_cajas_NO_produce_region() {
        // Este es el caso que el auditor marco y que el primer borrador del plan no cubria.
        // El detector partio el numero: ninguna caja lo contiene entero.
        List<MotorOcr.Linea> partido = new ArrayList<MotorOcr.Linea>(Arrays.asList(
                linea("AUT:", 10, 50, 60, 70),
                linea("8839", 80, 50, 130, 70),
                linea("21", 135, 50, 160, 70)
        ));

        DerivadorMapa.Resultado r = derivador.derivar(partido, ".*AUT: (?<auth>[0-9 ]+).*", 400, 200);

        assertTrue(r.ok(), r.error);
        DerivadorMapa.RegionPropuesta auth = r.regiones.get(0);
        assertFalse(auth.derivada(), "no deberia inventar una region");
        assertNull(auth.x1);
        assertTrue(auth.sinRegion.contains("repartido"), auth.sinRegion);
        assertEquals(1, r.sinRegion());
    }

    @Test
    public void un_valor_ambiguo_NO_produce_region() {
        // El mismo texto en dos lugares del cupon: no hay forma de saber cual es el bueno.
        List<MotorOcr.Linea> ambiguo = new ArrayList<MotorOcr.Linea>(Arrays.asList(
                linea("AUT: 100", 10, 50, 120, 70),
                linea("VUELTO 100", 10, 90, 130, 110)
        ));

        DerivadorMapa.Resultado r = derivador.derivar(ambiguo, ".*AUT: (?<auth>[0-9]+).*", 400, 200);

        DerivadorMapa.RegionPropuesta auth = r.regiones.get(0);
        assertFalse(auth.derivada());
        assertTrue(auth.sinRegion.contains("2 lugares"), auth.sinRegion);
    }

    @Test
    public void etiqueta_y_valor_en_la_misma_caja_se_resuelve_DENTRO() {
        List<MotorOcr.Linea> junto = new ArrayList<MotorOcr.Linea>(Arrays.asList(
                linea("AUT: 883921", 10, 50, 200, 70)
        ));

        DerivadorMapa.Resultado r = derivador.derivar(junto, ".*AUT: (?<auth>[0-9]+).*", 400, 200);

        DerivadorMapa.RegionPropuesta auth = r.regiones.get(0);
        assertTrue(auth.derivada(), auth.sinRegion);
        assertEquals("DENTRO", auth.posicion);
        assertEquals("AUT:", auth.etiqueta);
    }

    @Test
    public void si_no_hay_nada_a_la_izquierda_se_ancla_al_renglon_de_arriba() {
        List<MotorOcr.Linea> apilado = new ArrayList<MotorOcr.Linea>(Arrays.asList(
                linea("TERMINAL", 10, 50, 100, 70),
                linea("JF798SJJ", 10, 75, 110, 95)
        ));

        // \\s+ y no un espacio literal: estan en renglones distintos, asi que el texto trae \\n.
        DerivadorMapa.Resultado r = derivador.derivar(apilado, ".*TERMINAL\\s+(?<t>[A-Z0-9]+).*", 400, 200);

        DerivadorMapa.RegionPropuesta t = r.regiones.get(0);
        assertTrue(t.derivada(), t.sinRegion);
        assertEquals("ABAJO", t.posicion);
        assertEquals("TERMINAL", t.etiqueta);
    }

    @Test
    public void un_espacio_literal_no_cruza_renglones_pero_barra_s_si() {
        // Vale documentarlo porque es el error tipico al escribir un formato para un ticket:
        // el texto une con espacio lo que comparte renglon y con \n lo que no, asi que un patron
        // copiado de un QR --que nunca trae saltos-- deja de matchear en cuanto la etiqueta y el
        // valor caen en lineas distintas.
        List<MotorOcr.Linea> apilado = new ArrayList<MotorOcr.Linea>(Arrays.asList(
                linea("TERMINAL", 10, 50, 100, 70),
                linea("JF798SJJ", 10, 75, 110, 95)
        ));

        assertFalse(derivador.derivar(apilado, ".*TERMINAL (?<t>[A-Z0-9]+).*", 400, 200).ok(),
                "un espacio literal no deberia matchear un salto de renglon");
        assertTrue(derivador.derivar(apilado, ".*TERMINAL\\s+(?<t>[A-Z0-9]+).*", 400, 200).ok());
    }

    @Test
    public void un_patron_que_no_reconoce_el_cupon_se_reporta_antes_de_derivar_nada() {
        DerivadorMapa.Resultado r = derivador.derivar(cupon(), ".*NADA QUE VER (?<x>[0-9]+).*", 400, 200);

        assertFalse(r.ok());
        assertTrue(r.error.contains("no reconoce"), r.error);
        assertTrue(r.regiones.isEmpty());
    }

    @Test
    public void un_patron_sin_grupos_nombrados_no_tiene_nada_que_derivar() {
        DerivadorMapa.Resultado r = derivador.derivar(cupon(), ".*AUT.*", 400, 200);

        assertFalse(r.ok());
        assertTrue(r.error.contains("grupos nombrados"), r.error);
    }

    @Test
    public void entradas_invalidas_se_reportan_y_no_revientan() {
        assertFalse(derivador.derivar(null, ".*(?<a>.).*", 400, 200).ok());
        assertFalse(derivador.derivar(new ArrayList<MotorOcr.Linea>(), ".*(?<a>.).*", 400, 200).ok());
        assertFalse(derivador.derivar(cupon(), null, 400, 200).ok());
        assertFalse(derivador.derivar(cupon(), "(?<sinCerrar", 400, 200).ok());
        // Sin tamano de imagen no se puede normalizar, y normalizar contra 0 daria Infinity.
        assertFalse(derivador.derivar(cupon(), ".*(?<a>.).*", 0, 200).ok());
    }

    @Test
    public void la_derivacion_es_determinista() {
        // Correrla dos veces sobre el mismo cupon da el mismo mapa: no hay estado acumulado.
        // Es lo que permite ofrecerla como accion repetible desde el desktop.
        String patron = ".*AUT: (?<auth>[0-9]+).*MONTO: (?<monto>[0-9.]+).*";
        DerivadorMapa.Resultado a = derivador.derivar(cupon(), patron, 400, 200);
        DerivadorMapa.Resultado b = derivador.derivar(cupon(), patron, 400, 200);

        assertEquals(a.regiones.size(), b.regiones.size());
        for (int i = 0; i < a.regiones.size(); i++) {
            assertEquals(a.regiones.get(i).campo, b.regiones.get(i).campo);
            assertEquals(a.regiones.get(i).etiqueta, b.regiones.get(i).etiqueta);
            assertEquals(a.regiones.get(i).x1, b.regiones.get(i).x1);
            assertEquals(a.regiones.get(i).y2, b.regiones.get(i).y2);
        }
    }

    // ── Lo que encontro la prueba de punta a punta (2026-09-12) ────────────────────────────

    /** Un ticket termico real: el detector pega la etiqueta al valor en una sola caja. */
    private static List<MotorOcr.Linea> cuponTermico() {
        return new ArrayList<MotorOcr.Linea>(Arrays.asList(
                linea("FECHA:12/09/2026", 60, 250, 380, 285),
                linea("TERMINAL:JF798SJJ", 60, 300, 430, 335),
                linea("COMERCI0:00451233", 60, 350, 440, 385),
                linea("AUT:883921", 60, 405, 290, 440),
                linea("BOLETA:00045", 60, 455, 300, 490),
                linea("MONTO:150.000", 60, 520, 350, 555)
        ));
    }

    private static final String MAPEO_REAL =
            "{\"terminal\":{\"de\":\"terminal\"},"
            + "\"codigoAutorizacion\":{\"de\":\"auth\"},"
            + "\"numeroBoleta\":{\"de\":\"boleta\"},"
            + "\"monto\":{\"de\":\"monto\"}}";

    private static final String PATRON_REAL =
            "^[\\s\\S]*TERMINAL:\\s*(?<terminal>[A-Z0-9]+)"
            + "[\\s\\S]*AUT:\\s*(?<auth>[0-9]+)"
            + "[\\s\\S]*BOLETA:\\s*(?<boleta>[0-9]+)"
            + "[\\s\\S]*MONTO:\\s*(?<monto>[0-9.]+)[\\s\\S]*$";

    @Test
    public void la_region_se_nombra_con_el_campo_del_MAPEO_no_con_el_grupo_del_patron() {
        // EL defecto que encontro la prueba punta a punta. Sin el mapeo, la region salia como
        // "auth" y el ABM la rechazaba con "el mapeo no produce el campo auth": la derivacion
        // quedaba inservible salvo que alguien nombrara los grupos igual que las columnas.
        DerivadorMapa.Resultado r = derivador.derivar(
                cuponTermico(), PATRON_REAL, MAPEO_REAL, 720, 1000);

        assertTrue(r.ok(), r.error);
        List<String> campos = new ArrayList<String>();
        for (DerivadorMapa.RegionPropuesta p : r.regiones) campos.add(p.campo);

        assertTrue(campos.contains("codigoAutorizacion"), "esperaba el campo del mapeo, no el grupo: " + campos);
        assertTrue(campos.contains("numeroBoleta"), campos.toString());
        assertFalse(campos.contains("auth"), "el nombre del grupo no puede llegar como campo");
        assertFalse(campos.contains("boleta"), campos.toString());
    }

    @Test
    public void un_grupo_que_el_mapeo_no_menciona_conserva_su_nombre() {
        // Es un campo propio del proveedor: cae a datos_extra y el ABM decide si lo acepta.
        DerivadorMapa.Resultado r = derivador.derivar(
                cuponTermico(), PATRON_REAL, "{\"monto\":{\"de\":\"monto\"}}", 720, 1000);

        assertTrue(r.ok(), r.error);
        List<String> campos = new ArrayList<String>();
        for (DerivadorMapa.RegionPropuesta p : r.regiones) campos.add(p.campo);
        assertTrue(campos.contains("auth"), campos.toString());
        assertTrue(campos.contains("monto"), campos.toString());
    }

    @Test
    public void la_etiqueta_pegada_al_valor_gana_sobre_la_linea_de_arriba() {
        // El otro defecto de la prueba punta a punta. "TERMINAL:JF798SJJ" sale como UNA caja, y
        // antes el ancla se iba a la linea de arriba --"FECHA:12/09/2026"--, que es fragil y
        // ademas equivocada: la etiqueta correcta estaba en la misma caja.
        DerivadorMapa.Resultado r = derivador.derivar(
                cuponTermico(), PATRON_REAL, MAPEO_REAL, 720, 1000);

        DerivadorMapa.RegionPropuesta terminal = null, auth = null;
        for (DerivadorMapa.RegionPropuesta p : r.regiones) {
            if ("terminal".equals(p.campo)) terminal = p;
            if ("codigoAutorizacion".equals(p.campo)) auth = p;
        }

        assertNotNull(terminal);
        assertEquals("TERMINAL:", terminal.etiqueta);
        assertEquals("DENTRO", terminal.posicion);

        assertNotNull(auth);
        assertEquals("AUT:", auth.etiqueta);
        assertEquals("DENTRO", auth.posicion);
    }

    @Test
    public void sin_mapeo_sigue_andando_con_los_nombres_de_grupo() {
        // Compatibilidad: la firma vieja no se rompe.
        DerivadorMapa.Resultado r = derivador.derivar(cuponTermico(), PATRON_REAL, 720, 1000);

        assertTrue(r.ok(), r.error);
        assertEquals(4, r.regiones.size());
    }

    // ---- El tipo del campo lo declara el MAPEO, no la muestra ----
    //
    // Se intento deducirlo del valor leido y esta medido que falla: el codigo de autorizacion de
    // INFONET sale alfanumerico en credito (D380AD) y numerico en debito y QR (467769), por la
    // misma terminal. Deducir NUMERO desde un ticket de debito mandaria a revision toda venta con
    // credito. Una muestra no alcanza para afirmar el tipo de un campo.

    @Test
    void el_tipo_sale_del_mapeo() {
        String mapeo = "{\"codigoAutorizacion\":{\"de\":\"auth\",\"tipo\":\"TEXTO\"},"
                     + "\"numeroBoleta\":{\"de\":\"boleta\",\"tipo\":\"NUMERO\"}}";
        DerivadorMapa.Resultado r = derivador.derivar(cuponTermico(), PATRON_REAL, mapeo, 720, 1000);

        assertTrue(r.ok(), r.error);
        assertEquals("TEXTO", tipoDe(r, "codigoAutorizacion"));
        assertEquals("NUMERO", tipoDe(r, "numeroBoleta"));
    }

    @Test
    void un_campo_sin_tipo_declarado_queda_en_null() {
        // El default seguro: el filial no valida nada para ese campo. Declarar de mas cuesta una
        // lectura correcta convertida en sospecha; declarar de menos solo pierde una defensa.
        DerivadorMapa.Resultado r = derivador.derivar(
                cuponTermico(), PATRON_REAL, MAPEO_REAL, 720, 1000);

        assertTrue(r.ok(), r.error);
        assertNull(tipoDe(r, "codigoAutorizacion"),
                "MAPEO_REAL no declara tipo: no se puede inventar uno");
    }

    @Test
    void sin_mapeo_ningun_campo_tiene_tipo() {
        DerivadorMapa.Resultado r = derivador.derivar(cupon(),
                "^[\\s\\S]*AUT: (?<auth>[0-9]+)[\\s\\S]*$", 400, 200);

        assertTrue(r.ok(), r.error);
        for (DerivadorMapa.RegionPropuesta pr : r.regiones) assertNull(pr.tipo);
    }

    @Test
    void el_tipo_se_lee_aunque_venga_antes_que_el_de() {
        // El orden de las claves dentro del objeto es libre en JSON y el parseo es por regex: si
        // dependiera del orden, un mapeo valido perderia el tipo sin decir nada.
        String mapeo = "{\"codigoAutorizacion\":{\"tipo\":\"TEXTO\",\"de\":\"auth\"}}";
        DerivadorMapa.Resultado r = derivador.derivar(cuponTermico(), PATRON_REAL, mapeo, 720, 1000);

        assertTrue(r.ok(), r.error);
        assertEquals("TEXTO", tipoDe(r, "codigoAutorizacion"));
    }

    @Test
    void el_tipo_en_minuscula_se_normaliza() {
        String mapeo = "{\"codigoAutorizacion\":{\"de\":\"auth\",\"tipo\":\"numero\"}}";
        DerivadorMapa.Resultado r = derivador.derivar(cuponTermico(), PATRON_REAL, mapeo, 720, 1000);

        assertTrue(r.ok(), r.error);
        assertEquals("NUMERO", tipoDe(r, "codigoAutorizacion"),
                "el filial compara contra NUMERO en mayuscula; sin normalizar, no validaria nada");
    }

    @Test
    void un_tipo_mal_escrito_degrada_a_null_y_no_revienta() {
        // Es el motivo por el que el ABM del formato confronta este parseo contra el JSON real
        // antes de guardar: aca abajo el typo es indistinguible de "no declaro tipo".
        String mapeo = "{\"codigoAutorizacion\":{\"de\":\"auth\",\"tipo\":\"NUMER0\"}}";
        DerivadorMapa.Resultado r = derivador.derivar(cuponTermico(), PATRON_REAL, mapeo, 720, 1000);

        assertTrue(r.ok(), r.error);
        assertNull(tipoDe(r, "codigoAutorizacion"));
    }

    // ---- El valor que es cola de otro numero ----

    @Test
    void el_valor_que_es_COLA_de_otro_numero_no_es_ambiguo() {
        // Caso real de INFONET, 2026-09-15: el codigo de autorizacion es la cola del numero de
        // boleta --BOLETA:5671436954 y C.AUT:436954-- asi que buscar por "contiene" encontraba dos
        // cajas y el campo se quedaba sin region por una ambiguedad que no existe.
        List<MotorOcr.Linea> lineas = Arrays.asList(
                linea("BOLETA:5671436954", 40, 100, 300, 130),
                linea("C.AUT:436954", 40, 160, 260, 190));

        DerivadorMapa.Resultado r = derivador.derivar(lineas,
                "^[\\s\\S]*C\\.AUT:(?<auth>[0-9]+)[\\s\\S]*$", null, 400, 300);

        assertTrue(r.ok(), r.error);
        DerivadorMapa.RegionPropuesta p = r.regiones.get(0);
        assertNull(p.sinRegion, "no deberia declararse ambiguo: " + p.sinRegion);
        assertEquals(160 / 300.0, p.y1.doubleValue(), 0.01, "tiene que ser la caja de C.AUT");
    }

    @Test
    void si_aparece_entero_en_DOS_cajas_sigue_siendo_ambiguo() {
        // El desempate solo rescata lo que se descartaba por una coincidencia de digitos. Cuando la
        // ambiguedad es real, el campo tiene que seguir cayendo al patron.
        List<MotorOcr.Linea> lineas = Arrays.asList(
                linea("C.AUT:436954", 40, 100, 260, 130),
                linea("REF:436954", 40, 160, 240, 190));

        DerivadorMapa.Resultado r = derivador.derivar(lineas,
                "^[\\s\\S]*C\\.AUT:(?<auth>[0-9]+)[\\s\\S]*$", null, 400, 300);

        assertTrue(r.ok(), r.error);
        assertNotNull(r.regiones.get(0).sinRegion);
    }

    private static String tipoDe(DerivadorMapa.Resultado r, String campo) {
        for (DerivadorMapa.RegionPropuesta p : r.regiones) if (campo.equals(p.campo)) return p.tipo;
        return null;
    }
}
