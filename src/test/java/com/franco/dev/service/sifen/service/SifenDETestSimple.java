package com.franco.dev.service.sifen.service;

import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionDE;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.core.fields.request.de.*;
import com.roshka.sifen.core.types.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Test SIMPLIFICADO - Copia exacta del test original con mínimas modificaciones
 * Solo cambiamos el receptor para usar datos del cliente 194
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
public class SifenDETestSimple {

    private static final Logger logger = LoggerFactory.getLogger(SifenDETestSimple.class);

    @Test
    @Transactional
    @Commit
    public void testRecepcionDESimple() throws SifenException {
        logger.info("=== INICIANDO TEST DE RECEPCIÓN DE DE SIMPLE ===");
        
        LocalDateTime currentDate = LocalDateTime.now();

        // Grupo A
        DocumentoElectronico DE = new DocumentoElectronico();
        DE.setdFecFirma(currentDate);
        DE.setdSisFact((short) 1);

        // Grupo B
        TgOpeDE gOpeDE = new TgOpeDE();
        gOpeDE.setiTipEmi(TTipEmi.NORMAL);
        DE.setgOpeDE(gOpeDE);

        // Grupo C
        TgTimb gTimb = new TgTimb();
        gTimb.setiTiDE(TTiDE.FACTURA_ELECTRONICA);
        gTimb.setdNumTim(12557662);
        gTimb.setdEst("001");
        gTimb.setdPunExp("002");
        gTimb.setdNumDoc("0000007");
        gTimb.setdFeIniT(LocalDate.parse("2019-07-31"));
        DE.setgTimb(gTimb);

        // Grupo D
        TdDatGralOpe dDatGralOpe = new TdDatGralOpe();
        dDatGralOpe.setdFeEmiDE(currentDate);

        TgOpeCom gOpeCom = new TgOpeCom();
        gOpeCom.setiTipTra(TTipTra.PRESTACION_SERVICIOS);
        gOpeCom.setiTImp(TTImp.IVA);
        gOpeCom.setcMoneOpe(CMondT.PYG);
        dDatGralOpe.setgOpeCom(gOpeCom);

        TgEmis gEmis = new TgEmis();
        gEmis.setdRucEm("80080553");
        gEmis.setdDVEmi("4");
        gEmis.setiTipCont(TiTipCont.PERSONA_JURIDICA);
        gEmis.setdNomEmi("DE generado en ambiente de prueba - sin valor comercial ni fiscal");
        gEmis.setdDirEmi("Mayor Bullo");
        gEmis.setdNumCas("670");
        gEmis.setcDepEmi(TDepartamento.CAPITAL);
        gEmis.setcCiuEmi(1);
        gEmis.setdDesCiuEmi("ASUNCION (DISTRITO)");
        gEmis.setdTelEmi("212376717");
        gEmis.setdEmailE("administracion@taxare.com.py");

        List<TgActEco> gActEcoList = new ArrayList<>();
        TgActEco gActEco = new TgActEco();
        gActEco.setcActEco("69209");
        gActEco.setdDesActEco("ACTIVIDADES DE CONTABILIDAD, TENEDURÍA DE LIBROS, AUDITORIA Y ASESORIA FISCAL N.C.P.");
        gActEcoList.add(gActEco);

        TgActEco gActEco2 = new TgActEco();
        gActEco2.setcActEco("62090");
        gActEco2.setdDesActEco("OTRAS ACTIVIDADES DE TECNOLOGÍA DE LA INFORMACIÓN Y SERVICIOS INFORMÁTICOS");
        gActEcoList.add(gActEco2);

        gEmis.setgActEcoList(gActEcoList);
        dDatGralOpe.setgEmis(gEmis);

        // RECEPTOR - Solo cambiar estos datos del original
        TgDatRec gDatRec = new TgDatRec();
        gDatRec.setiNatRec(TiNatRec.CONTRIBUYENTE); // CAMBIADO: era NO_CONTRIBUYENTE
        gDatRec.setiTiOpe(TiTiOpe.B2C);
        gDatRec.setcPaisRec(PaisType.PRY);
        gDatRec.setiTiContRec(TiTipCont.PERSONA_FISICA); // AGREGADO: Tipo de contribuyente
        gDatRec.setiTipIDRec(TiTipDocRec.CEDULA_PARAGUAYA);
        gDatRec.setdNumIDRec("5364471"); // CAMBIADO: cliente 194
        gDatRec.setdNomRec("PERALTA CRISTIAN RAFAEL"); // CAMBIADO: cliente 194
        dDatGralOpe.setgDatRec(gDatRec);
        DE.setgDatGralOpe(dDatGralOpe);

        // Grupo E
        TgDtipDE gDtipDE = new TgDtipDE();

        TgCamFE gCamFE = new TgCamFE();
        gCamFE.setiIndPres(TiIndPres.OPERACION_ELECTRONICA);
        gDtipDE.setgCamFE(gCamFE);

        TgCamCond gCamCond = new TgCamCond();
        gCamCond.setiCondOpe(TiCondOpe.CREDITO);
        
        // Inicializar lista de formas de pago (evita NullPointerException en setupSOAPElements)
        gCamCond.setgPaConEIniList(new ArrayList<>());

        TgPagCred gPagCred = new TgPagCred();
        gPagCred.setiCondCred(TiCondCred.PLAZO);
        gPagCred.setdPlazoCre("60 días");

        gCamCond.setgPagCred(gPagCred);
        gDtipDE.setgCamCond(gCamCond);

        List<TgCamItem> gCamItemList = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            TgCamItem gCamItem = new TgCamItem();
            gCamItem.setdCodInt(i == 0 ? "001" : "002");
            gCamItem.setdDesProSer(i == 0 ? "Servicio de Liquidación de IVA" : "Servicio de Liquidación de IRP");
            gCamItem.setcUniMed(TcUniMed.UNI);
            gCamItem.setdCantProSer(BigDecimal.valueOf(1));

            TgValorItem gValorItem = new TgValorItem();
            gValorItem.setdPUniProSer(BigDecimal.valueOf(120000));

            TgValorRestaItem gValorRestaItem = new TgValorRestaItem();
            gValorItem.setgValorRestaItem(gValorRestaItem);
            gCamItem.setgValorItem(gValorItem);

            TgCamIVA gCamIVA = new TgCamIVA();
            gCamIVA.setiAfecIVA(TiAfecIVA.GRAVADO);
            gCamIVA.setdPropIVA(BigDecimal.valueOf(100));
            gCamIVA.setdTasaIVA(BigDecimal.valueOf(10));
            gCamItem.setgCamIVA(gCamIVA);

            gCamItemList.add(gCamItem);
        }

        gDtipDE.setgCamItemList(gCamItemList);
        DE.setgDtipDE(gDtipDE);

        // Grupo F
        DE.setgTotSub(new TgTotSub());

        logger.info("CDC del Documento Electrónico -> " + DE.obtenerCDC());

        RespuestaRecepcionDE ret = Sifen.recepcionDE(DE);
        logger.info("Respuesta de recepción DE: {}", ret.toString());
        logger.info("Código de respuesta: {}", ret.getdCodRes());
        logger.info("Mensaje de respuesta: {}", ret.getdMsgRes());
        
        logger.info("=== TEST DE RECEPCIÓN DE DE SIMPLE COMPLETADO ===");
    }
}
