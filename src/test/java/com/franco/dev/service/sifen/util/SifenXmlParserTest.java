package com.franco.dev.service.sifen.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitario para SifenXmlParser.
 * Verifica que la extracción de tags del XML funcione correctamente.
 */
public class SifenXmlParserTest {

    // XML de ejemplo basado en la estructura real de SIFEN
    private static final String SAMPLE_XML = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">" +
        "  <env:Body>" +
        "    <rEnviDe xmlns=\"http://ekuatia.set.gov.py/sifen/xsd\">" +
        "      <dId>1</dId>" +
        "      <xDE>" +
        "        <rDE>" +
        "          <DE Id=\"01800994825001001000004422025100217524820275\">" +
        "            <gDatGralOpe>" +
        "              <dFeEmiDE>2025-10-02T14:07:03</dFeEmiDE>" +
        "            </gDatGralOpe>" +
        "            <gTotSub>" +
        "              <dTotGralOpe>67000</dTotGralOpe>" +
        "              <dTotIVA>6092</dTotIVA>" +
        "            </gTotSub>" +
        "          </DE>" +
        "          <Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">" +
        "            <SignedInfo>" +
        "              <Reference>" +
        "                <DigestValue>Ng0orkp4713ZwA/bUz7bibj5IsgorfiIGrrJ8rYNgDI=</DigestValue>" +
        "              </Reference>" +
        "            </SignedInfo>" +
        "          </Signature>" +
        "          <gCamFuFD>" +
        "            <dCarQR>https://ekuatia.set.gov.py/consultas/qr?nVersion=150&amp;Id=01800994825001001000004422025100217524820275&amp;dFeEmiDE=323032352d31302d30325431343a30373a3033&amp;dRucRec=4043581&amp;dTotGralOpe=67000&amp;dTotIVA=6092&amp;cItems=3&amp;DigestValue=4e67306f726b70343731335a77412f62557a376269626a354973676f726669494772724a3872594e6744493d&amp;IdCSC=0001&amp;cHashQR=f88cf185b7116566fc58c960f403da8f8e5d1cdf3c30bb7747b944dc01b4b47b</dCarQR>" +
        "          </gCamFuFD>" +
        "        </rDE>" +
        "      </xDE>" +
        "    </rEnviDe>" +
        "  </env:Body>" +
        "</env:Envelope>";

    @Test
    public void testExtractUrlQr() {
        String urlQr = SifenXmlParser.extractUrlQr(SAMPLE_XML);
        
        assertNotNull(urlQr, "URL QR no debería ser null");
        assertTrue(urlQr.startsWith("https://ekuatia.set.gov.py/consultas/qr"), "URL QR debería comenzar con la base correcta");
        assertTrue(urlQr.contains("nVersion=150"), "URL QR debería contener nVersion");
        assertTrue(urlQr.contains("Id=01800994825001001000004422025100217524820275"), "URL QR debería contener el CDC");
        
        // Verificar que las entidades HTML fueron decodificadas
        assertTrue(urlQr.contains("&Id="), "Las entidades &amp; deberían estar decodificadas a &");
        assertFalse(urlQr.contains("&amp;"), "No debería contener entidades HTML sin decodificar");
    }

    @Test
    public void testExtractCdc() {
        String cdc = SifenXmlParser.extractCdc(SAMPLE_XML);
        
        assertNotNull(cdc, "CDC no debería ser null");
        assertEquals("01800994825001001000004422025100217524820275", cdc, "CDC debería coincidir");
        assertEquals(44, cdc.length(), "CDC debería tener 44 caracteres");
    }

    @Test
    public void testExtractDigestValue() {
        String digestValue = SifenXmlParser.extractDigestValue(SAMPLE_XML);
        
        assertNotNull(digestValue, "DigestValue no debería ser null");
        assertEquals("Ng0orkp4713ZwA/bUz7bibj5IsgorfiIGrrJ8rYNgDI=", digestValue, "DigestValue debería coincidir");
    }

    @Test
    public void testExtractFechaEmision() {
        String fecha = SifenXmlParser.extractFechaEmision(SAMPLE_XML);
        
        assertNotNull(fecha, "Fecha de emisión no debería ser null");
        assertEquals("2025-10-02T14:07:03", fecha, "Fecha de emisión debería coincidir");
    }

    @Test
    public void testExtractTotalGeneral() {
        String total = SifenXmlParser.extractTotalGeneral(SAMPLE_XML);
        
        assertNotNull(total, "Total general no debería ser null");
        assertEquals("67000", total, "Total general debería coincidir");
    }

    @Test
    public void testExtractTotalIva() {
        String totalIva = SifenXmlParser.extractTotalIva(SAMPLE_XML);
        
        assertNotNull(totalIva, "Total IVA no debería ser null");
        assertEquals("6092", totalIva, "Total IVA debería coincidir");
    }

    @Test
    public void testExtractTagValue() {
        String dId = SifenXmlParser.extractTagValue(SAMPLE_XML, "dId");
        
        assertNotNull(dId, "dId no debería ser null");
        assertEquals("1", dId, "dId debería ser 1");
    }

    @Test
    public void testExtractAttributeValue() {
        String id = SifenXmlParser.extractAttributeValue(SAMPLE_XML, "DE", "Id");
        
        assertNotNull(id, "Atributo Id no debería ser null");
        assertEquals("01800994825001001000004422025100217524820275", id, "Atributo Id debería coincidir");
    }

    @Test
    public void testHasTag() {
        assertTrue(SifenXmlParser.hasTag(SAMPLE_XML, "dCarQR"), "Debería encontrar el tag dCarQR");
        assertTrue(SifenXmlParser.hasTag(SAMPLE_XML, "dTotGralOpe"), "Debería encontrar el tag dTotGralOpe");
        assertFalse(SifenXmlParser.hasTag(SAMPLE_XML, "tagInexistente"), "No debería encontrar un tag inexistente");
    }

    @Test
    public void testExtractFullTag() {
        String gTotSub = SifenXmlParser.extractFullTag(SAMPLE_XML, "gTotSub");
        
        assertNotNull(gTotSub, "gTotSub completo no debería ser null");
        assertTrue(gTotSub.startsWith("<gTotSub>"), "Debería comenzar con el tag de apertura");
        assertTrue(gTotSub.endsWith("</gTotSub>"), "Debería terminar con el tag de cierre");
        assertTrue(gTotSub.contains("<dTotGralOpe>67000</dTotGralOpe>"), "Debería contener el contenido interno");
    }

    @Test
    public void testExtractTagValueNull() {
        assertNull(SifenXmlParser.extractTagValue(null, "dCarQR"), "Debería retornar null para XML null");
        assertNull(SifenXmlParser.extractTagValue(SAMPLE_XML, null), "Debería retornar null para tagName null");
        assertNull(SifenXmlParser.extractTagValue(SAMPLE_XML, ""), "Debería retornar null para tagName vacío");
        assertNull(SifenXmlParser.extractTagValue(SAMPLE_XML, "tagInexistente"), "Debería retornar null para tag inexistente");
    }

    @Test
    public void testExtractTagValueWithPrefix() {
        String xmlWithPrefix = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<ns2:rResEnviConsLoteDe xmlns:ns2=\"http://ekuatia.set.gov.py/sifen/xsd\">" +
            "  <ns2:dCodResLot>0362</ns2:dCodResLot>" +
            "  <ns2:gResProcLote>" +
            "    <ns2:dEstRes>Aprobado</ns2:dEstRes>" +
            "  </ns2:gResProcLote>" +
            "</ns2:rResEnviConsLoteDe>";
        
        String codigoLote = SifenXmlParser.extractTagValueWithPrefix(xmlWithPrefix, "dCodResLot", "ns2");
        assertEquals("0362", codigoLote, "Debería extraer el código del lote con prefijo");
        
        String estadoRes = SifenXmlParser.extractTagValueWithPrefix(xmlWithPrefix, "dEstRes", "ns2");
        assertEquals("Aprobado", estadoRes, "Debería extraer el estado con prefijo");
    }
}

