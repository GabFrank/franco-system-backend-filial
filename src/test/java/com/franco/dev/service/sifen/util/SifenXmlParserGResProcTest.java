package com.franco.dev.service.sifen.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * El resultado de procesamiento del documento vive en gResProc. El dCodRes de nivel de
 * respuesta es el de la consulta en si (0422 = CDC encontrado), asi que tomar el primero
 * del XML guardaria un codigo que no corresponde al documento.
 */
class SifenXmlParserGResProcTest {

    /** Respuesta tipica de consulta individual: 0422 arriba, resultado del DE en gResProc. */
    private static final String RESPUESTA_CON_NS2 =
        "<env:Envelope><env:Body><ns2:rEnviConsDeResponse xmlns:ns2=\"http://ekuatia.set.gov.py/sifen/xsd\">"
            + "<ns2:dCodRes>0422</ns2:dCodRes>"
            + "<ns2:dMsgRes>CDC encontrado</ns2:dMsgRes>"
            + "<ns2:xContenDE><ns2:xProtDe>"
            + "<ns2:dEstRes>Aprobado</ns2:dEstRes>"
            + "<ns2:dProtAut>2658109949</ns2:dProtAut>"
            + "<ns2:gResProc>"
            + "<ns2:dCodRes>0260</ns2:dCodRes>"
            + "<ns2:dMsgRes>Autorización del DE &amp; su timbrado</ns2:dMsgRes>"
            + "</ns2:gResProc>"
            + "</ns2:xProtDe></ns2:xContenDE>"
            + "</ns2:rEnviConsDeResponse></env:Body></env:Envelope>";

    private static final String RESPUESTA_SIN_PREFIJO =
        "<rEnviConsDeResponse>"
            + "<dCodRes>0422</dCodRes><dMsgRes>CDC encontrado</dMsgRes>"
            + "<gResProc><dCodRes>0420</dCodRes><dMsgRes>DE rechazado</dMsgRes></gResProc>"
            + "</rEnviConsDeResponse>";

    @Test
    void tomaElCodigoDelDocumentoNoElDeLaConsulta() {
        assertEquals("0260", SifenXmlParser.extractGResProcValue(RESPUESTA_CON_NS2, "dCodRes"));
    }

    @Test
    void extraeElMensajeDecodificandoEntidades() {
        assertEquals("Autorización del DE & su timbrado",
            SifenXmlParser.extractGResProcValue(RESPUESTA_CON_NS2, "dMsgRes"));
    }

    @Test
    void funcionaSinPrefijoDeNamespace() {
        assertEquals("0420", SifenXmlParser.extractGResProcValue(RESPUESTA_SIN_PREFIJO, "dCodRes"));
        assertEquals("DE rechazado", SifenXmlParser.extractGResProcValue(RESPUESTA_SIN_PREFIJO, "dMsgRes"));
    }

    @Test
    void devuelveNullSiNoHayBloqueGResProc() {
        String sinBloque = "<rEnviConsDeResponse><dCodRes>0420</dCodRes>"
            + "<dMsgRes>CDC no encontrado</dMsgRes></rEnviConsDeResponse>";

        assertNull(SifenXmlParser.extractGResProcValue(sinBloque, "dCodRes"));
    }

    @Test
    void devuelveNullConEntradasVacias() {
        assertNull(SifenXmlParser.extractGResProcValue(null, "dCodRes"));
        assertNull(SifenXmlParser.extractGResProcValue("", "dCodRes"));
        assertNull(SifenXmlParser.extractGResProcValue(RESPUESTA_CON_NS2, null));
        assertNull(SifenXmlParser.extractGResProcValue(RESPUESTA_CON_NS2, "dNoExiste"));
    }
}
