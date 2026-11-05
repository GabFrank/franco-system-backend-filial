package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.beans.EventosDE;
import com.roshka.sifen.core.fields.request.event.TgGroupTiEvt;
import com.roshka.sifen.core.fields.request.event.TrGeVeCan;
import com.roshka.sifen.core.fields.request.event.TrGesEve;
import com.roshka.sifen.internal.ctx.GenerationCtx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPMessage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Genera y firma el XML del DE con la libreria real, como crearDocumentoElectronico al guardar una
 * venta. Con saaj-impl 1.5.3 (la que trae Spring Boot 2.7) setIdAttributeNode tira NOT_FOUND_ERR y
 * no se puede facturar: paso en la 5.5.1. Los demas tests de SIFEN reemplazan
 * crearDocumentoElectronico, asi que ninguno llegaba a generarXml.
 */
class SifenGenerarXmlTest {

    private static final String CLAVE = "prueba";

    @TempDir
    Path dir;

    private SifenConfig config() throws Exception {
        SifenConfig config = new SifenConfig();
        config.setUsarCertificadoCliente(true);
        config.setTipoCertificadoCliente(SifenConfig.TipoCertificadoCliente.PFX);
        config.setCertificadoCliente(certificadoDescartable());
        config.setContrasenaCertificadoCliente(CLAVE);
        return config;
    }

    /** Cancelacion, inutilizacion y conformidad pasan por el mismo setIdAttributeNode (TrGesEve). */
    @Test
    void armaYFirmaUnEventoDeCancelacion() throws Exception {
        TrGeVeCan cancelacion = new TrGeVeCan();
        cancelacion.setId("01800000000000000000000000000000000000000000");
        cancelacion.setmOtEve("Prueba de cancelacion");
        TgGroupTiEvt tipo = new TgGroupTiEvt();
        tipo.setrGeVeCan(cancelacion);
        TrGesEve gestion = new TrGesEve();
        gestion.setId("1");
        gestion.setdFecFirma(LocalDateTime.now());
        gestion.setgGroupTiEvt(tipo);
        EventosDE eventos = new EventosDE();
        eventos.setrGesEveList(Collections.singletonList(gestion));

        SOAPMessage msg = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL).createMessage();
        SOAPElement raiz = msg.getSOAPBody().addChildElement("rEnviEventoDe", "", "http://ekuatia.set.gov.py/sifen/xsd");
        eventos.setupSOAPElements(raiz, config());

        ByteArrayOutputStream xml = new ByteArrayOutputStream();
        msg.writeTo(xml);
        assertTrue(xml.toString("UTF-8").contains("SignatureValue"), xml.toString("UTF-8"));
    }

    @Test
    void generaYFirmaElXmlDelDE() throws Exception {
        SifenConfig config = config();
        SifenService service = new SifenService(null, null, null, null, null, null, null, null, config);
        List<FacturaLegalItem> items =
                Collections.singletonList(FacturaElectronicaDePrueba.item("ITEM", 1f, 11000.0));

        DocumentoElectronico de = ReflectionTestUtils.invokeMethod(service,
                "generarDEDesdeFacturaDatosReales", FacturaElectronicaDePrueba.factura(), items);
        String xml = de.generarXml(GenerationCtx.getDefaultFromConfig(config));

        assertTrue(xml.contains("Id=\"" + de.obtenerCDC() + "\""), xml);
        assertTrue(xml.contains("SignatureValue"), xml);
    }

    /** Autofirmado, solo para firmar en el test: lo crea keytool del JDK que corre los tests. */
    private String certificadoDescartable() throws Exception {
        File pfx = dir.resolve("prueba.pfx").toFile();
        String keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool").toString();
        Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "prueba", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "1", "-dname", "CN=prueba", "-storetype", "PKCS12",
                "-keystore", pfx.getPath(), "-storepass", CLAVE, "-keypass", CLAVE)
                .redirectErrorStream(true).start();
        assertEquals(0, p.waitFor(), "keytool");
        return pfx.getPath();
    }
}
