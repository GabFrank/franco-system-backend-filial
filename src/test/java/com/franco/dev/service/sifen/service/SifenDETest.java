package com.franco.dev.service.sifen.service;

import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.beans.EventosDE;
import com.roshka.sifen.core.beans.response.RespuestaConsultaDE;
import com.roshka.sifen.core.beans.response.RespuestaConsultaLoteDE;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionDE;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionEvento;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionLoteDE;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.core.fields.request.de.*;
import com.roshka.sifen.core.fields.request.event.TgGroupTiEvt;
import com.roshka.sifen.core.fields.request.event.TrGeVeDisconf;
import com.roshka.sifen.core.fields.request.event.TrGesEve;
import com.roshka.sifen.core.types.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.sifen.util.CodigosGeograficos;
import com.franco.dev.utilitarios.CalcularVerificadorRuc;

import java.lang.reflect.Field;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test adaptado para usar datos reales de FacturaLegal del proyecto.
 * Basado en el test original de la librería SIFEN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
public class SifenDETest {

    private static final Logger logger = LoggerFactory.getLogger(SifenDETest.class);
    
    @Autowired
    private FacturaLegalService facturaLegalService;
    
    @Autowired
    private FacturaLegalItemService facturaLegalItemService;
    
    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;
    
    @Autowired
    private LoteDEService loteDEService;
    
    @Autowired
    private ClienteService clienteService;
    
    @Autowired
    private TimbradoDetalleService timbradoDetalleService;
    
    // IDs de prueba
    private static final Long FACTURA_LEGAL_TEST_ID = 11843L;

    /**
     * Genera una nueva factura con datos específicos para testing
     * 
     * @return FacturaLegal generada
     */
    private FacturaLegal generarFacturaParaTest() {
        logger.info("=== GENERANDO NUEVA FACTURA PARA TEST ===");
        
        // Crear nueva factura
        FacturaLegal factura = new FacturaLegal();
        factura.setSucursalId(24L); // Sucursal específica
        factura.setFecha(LocalDateTime.now());
        factura.setCredito(false); // Contado
        
        // Cargar cliente y timbrado detalle
        Cliente cliente = clienteService.findById(194L).orElse(null);
        if (cliente == null) {
            throw new IllegalArgumentException("Cliente con ID 194 no encontrado");
        }
        factura.setCliente(cliente);
        
        TimbradoDetalle timbradoDetalle = timbradoDetalleService.findById(93L).orElse(null);
        if (timbradoDetalle == null) {
            throw new IllegalArgumentException("TimbradoDetalle con ID 93 no encontrado");
        }
        factura.setTimbradoDetalle(timbradoDetalle);
        
        // CORREGIDO: Obtener siguiente número correlativo del timbrado detalle
        Long siguienteNumero = timbradoDetalle.getNumeroActual() != null ? 
            timbradoDetalle.getNumeroActual() + 1 : 1L;
        factura.setNumeroFactura(siguienteNumero.intValue());
        
        logger.info("✅ Número de factura asignado: {} (anterior: {})", 
            siguienteNumero, timbradoDetalle.getNumeroActual());
        
        // Guardar factura
        factura = facturaLegalService.save(factura);
        logger.info("✅ Factura creada con ID: {}", factura.getId());
        
        // Crear 3 items aleatorios
        List<FacturaLegalItem> items = crearItemsAleatorios(factura);
        
        // Calcular totales
        double totalItems = items.stream()
            .mapToDouble(item -> item.getCantidad().floatValue() * item.getPrecioUnitario().doubleValue())
            .sum();
        
        double totalIva = totalItems * 0.10; // 10% IVA
        double totalFinal = totalItems + totalIva;
        
        factura.setTotalFinal(totalFinal);
        
        // Actualizar factura con totales
        factura = facturaLegalService.save(factura);
        
        // CORREGIDO: Actualizar número actual en TimbradoDetalle
        timbradoDetalle.setNumeroActual(siguienteNumero);
        timbradoDetalleService.save(timbradoDetalle);
        logger.info("✅ TimbradoDetalle actualizado - nuevo numeroActual: {}", siguienteNumero);
        
        logger.info("✅ Factura completada - Total: {}, Items: {}", totalFinal, items.size());
        logger.info("=== FIN DE GENERACIÓN DE FACTURA ===");
        
        return factura;
    }
    
    /**
     * Crea 3 items aleatorios para la factura
     * 
     * @param factura FacturaLegal asociada
     * @return Lista de items creados
     */
    private List<FacturaLegalItem> crearItemsAleatorios(FacturaLegal factura) {
        logger.info("=== CREANDO ITEMS ALEATORIOS ===");
        
        List<FacturaLegalItem> items = new ArrayList<>();
        
        // Item 1: Producto de ejemplo
        FacturaLegalItem item1 = new FacturaLegalItem();
        item1.setFacturaLegal(factura);
        item1.setSucursalId(factura.getSucursalId());
        item1.setDescripcion("TRES LEONES ETIQUETA NEGRA 750ML");
        item1.setCantidad(2.0f);
        item1.setPrecioUnitario(15000.0);
        item1.setTotal(30000.0);
        item1 = facturaLegalItemService.save(item1);
        items.add(item1);
        logger.info("✅ Item 1 creado: {} x {} = {}", item1.getDescripcion(), item1.getCantidad(), item1.getTotal());
        
        // Item 2: Producto de ejemplo
        FacturaLegalItem item2 = new FacturaLegalItem();
        item2.setFacturaLegal(factura);
        item2.setSucursalId(factura.getSucursalId());
        item2.setDescripcion("COCA COLA 500ML");
        item2.setCantidad(3.0f);
        item2.setPrecioUnitario(8000.0);
        item2.setTotal(24000.0);
        item2 = facturaLegalItemService.save(item2);
        items.add(item2);
        logger.info("✅ Item 2 creado: {} x {} = {}", item2.getDescripcion(), item2.getCantidad(), item2.getTotal());
        
        // Item 3: Producto de ejemplo
        FacturaLegalItem item3 = new FacturaLegalItem();
        item3.setFacturaLegal(factura);
        item3.setSucursalId(factura.getSucursalId());
        item3.setDescripcion("PAN INTEGRAL 500G");
        item3.setCantidad(1.0f);
        item3.setPrecioUnitario(12000.0);
        item3.setTotal(12000.0);
        item3 = facturaLegalItemService.save(item3);
        items.add(item3);
        logger.info("✅ Item 3 creado: {} x {} = {}", item3.getDescripcion(), item3.getCantidad(), item3.getTotal());
        
        logger.info("✅ Total de items creados: {}", items.size());
        logger.info("=== FIN DE CREACIÓN DE ITEMS ===");
        
        return items;
    }


    @Test
    @Transactional
    @Commit
    public void testRecepcionDEDatosReales() throws Exception {
        logger.info("=== INICIANDO TEST DE RECEPCIÓN DE DE CON DATOS REALES ===");
        
        // Generar nueva factura para test
        FacturaLegal factura = generarFacturaParaTest();
        logger.info("✅ Factura generada para test con ID: {}", factura.getId());
        
        logger.info("Factura cargada: ID={}, Cliente={}, Total={}", 
            factura.getId(), 
            factura.getCliente() != null ? factura.getCliente().getId() : "N/A",
            factura.getTotalFinal());
        
        // Generar DE con datos reales
        com.roshka.sifen.core.beans.DocumentoElectronico deSifen = generarDEDesdeFacturaDatosReales(factura);
        
        // Crear lote con un solo DE
        List<com.roshka.sifen.core.beans.DocumentoElectronico> loteDeUno = new ArrayList<>();
        loteDeUno.add(deSifen);
        
        // Aplicar fix para totales IVA antes de enviar
        TgTotSub totalesFinales = deSifen.getgTotSub();
        aplicarFixTotalesIVA(totalesFinales);
        
        logger.info("=== ENVIANDO LOTE A SIFEN ===");
        RespuestaRecepcionLoteDE respuestaLote = Sifen.recepcionLoteDE(loteDeUno);
        
        logger.info("=== RESPUESTA DE SIFEN ===");
        logger.info("Código de respuesta: {}", respuestaLote.getdCodRes());
        logger.info("Mensaje de respuesta: {}", respuestaLote.getdMsgRes());
        
        // CORREGIDO: Crear objeto RespuestaRecepcionDE compatible para guardar en BD
        RespuestaRecepcionDE respuesta = new RespuestaRecepcionDE();
        respuesta.setCodigoEstado(respuestaLote.getCodigoEstado());
        respuesta.setdCodRes(respuestaLote.getdCodRes());
        respuesta.setdMsgRes(respuestaLote.getdMsgRes());
        respuesta.setRespuestaBruta(respuestaLote.getRespuestaBruta());
        
        logger.info("=== RESPUESTA ADAPTADA PARA DE INDIVIDUAL ===");
        logger.info("Código HTTP: {}", respuesta.getCodigoEstado());
        logger.info("Código de respuesta: {}", respuesta.getdCodRes());
        logger.info("Mensaje de respuesta: {}", respuesta.getdMsgRes());
        
        // CORREGIDO: Agregar lógica de parsing de respuesta bruta (igual que método que funciona)
        if (respuesta.getdCodRes() == null && respuesta.getRespuestaBruta() != null) {
            logger.warn("⚠️ Códigos null - Parseando respuesta bruta...");
            logger.info("Respuesta bruta SIFEN: {}", respuesta.getRespuestaBruta());
            
            // Intentar extraer información de la respuesta XML
            String respuestaBruta = respuesta.getRespuestaBruta();
            if (respuestaBruta.contains("dCodRes")) {
                int startCod = respuestaBruta.indexOf("<ns2:dCodRes>") + 13;
                int endCod = respuestaBruta.indexOf("</ns2:dCodRes>");
                if (startCod > 13 && endCod > startCod) {
                    String codigo = respuestaBruta.substring(startCod, endCod);
                    logger.info("Código extraído de XML: {}", codigo);
                }
            }
            if (respuestaBruta.contains("dMsgRes")) {
                int startMsg = respuestaBruta.indexOf("<ns2:dMsgRes>") + 13;
                int endMsg = respuestaBruta.indexOf("</ns2:dMsgRes>");
                if (startMsg > 13 && endMsg > startMsg) {
                    String mensaje = respuestaBruta.substring(startMsg, endMsg);
                    logger.info("Mensaje extraído de XML: {}", mensaje);
                }
            }
            if (respuestaBruta.contains("dEstRes")) {
                int startEst = respuestaBruta.indexOf("<ns2:dEstRes>") + 13;
                int endEst = respuestaBruta.indexOf("</ns2:dEstRes>");
                if (startEst > 13 && endEst > startEst) {
                    String estado = respuestaBruta.substring(startEst, endEst);
                    logger.info("Estado extraído de XML: {}", estado);
                }
            }
        }
        
        logger.info("Respuesta toString(): {}", respuesta.toString());
        
        // Guardar en BD
        guardarLoteYDocumento(factura, deSifen, respuesta, respuestaLote);
        
        logger.info("=== TEST DE RECEPCIÓN DE DE CON DATOS REALES COMPLETADO ===");
    }

    @Test
    @Transactional
    @Commit
    public void testRecepcionDE() throws Exception {
        logger.info("=== INICIANDO TEST DE RECEPCIÓN DE DE INDIVIDUAL CON DATOS REALES ===");
        
        // Cargar la factura legal de la base de datos
        java.util.Optional<FacturaLegal> facturaOpt = facturaLegalService.findById(FACTURA_LEGAL_TEST_ID);
        assertNotNull(facturaOpt, "Debe existir la factura legal con ID: " + FACTURA_LEGAL_TEST_ID);
        FacturaLegal factura = facturaOpt.get();
        
        logger.info("Factura Legal cargada: ID={}, Número={}, Total={}", 
            factura.getId(), factura.getNumeroFactura(), factura.getTotalFinal());
        
        logger.info("Usando datos del Cliente ID 194: RUC=5364471-9, Razón Social=PERALTA CRISTIAN RAFAEL (Contribuyente)");
        
        // Generar el Documento Electrónico desde la factura
        DocumentoElectronico DE = generarDEDesdeFactura(factura);
        
        logger.info("=== DATOS DEL DE GENERADO ===");
        logger.info("CDC: {}", DE.obtenerCDC());
        
        // Logs de datos del Emisor
        logger.info("=== EMISOR ===");
        logger.info("RUC Emisor: {}-{}", DE.getgDatGralOpe().getgEmis().getdRucEm(), DE.getgDatGralOpe().getgEmis().getdDVEmi());
        logger.info("Nombre Emisor: {}", DE.getgDatGralOpe().getgEmis().getdNomEmi());
        logger.info("Tipo Contribuyente Emisor: {}", DE.getgDatGralOpe().getgEmis().getiTipCont());
        logger.info("Email Emisor: {}", DE.getgDatGralOpe().getgEmis().getdEmailE());
        
        // Logs de datos del Receptor
        logger.info("=== RECEPTOR ===");
        logger.info("Naturaleza Receptor: {}", DE.getgDatGralOpe().getgDatRec().getiNatRec());
        logger.info("Tipo Contribuyente Receptor: {}", DE.getgDatGralOpe().getgDatRec().getiTiContRec());
        logger.info("Tipo Operación: {}", DE.getgDatGralOpe().getgDatRec().getiTiOpe());
        logger.info("Tipo ID Receptor: {}", DE.getgDatGralOpe().getgDatRec().getiTipIDRec());
        logger.info("Número ID Receptor: {}", DE.getgDatGralOpe().getgDatRec().getdNumIDRec());
        logger.info("Nombre Receptor: {}", DE.getgDatGralOpe().getgDatRec().getdNomRec());
        
        // Logs de Timbrado
        logger.info("=== TIMBRADO ===");
        logger.info("Número Timbrado: {}", DE.getgTimb().getdNumTim());
        logger.info("Tipo DE: {}", DE.getgTimb().getiTiDE());
        logger.info("Establecimiento: {}", DE.getgTimb().getdEst());
        logger.info("Punto Expedición: {}", DE.getgTimb().getdPunExp());
        logger.info("Número Documento: {}", DE.getgTimb().getdNumDoc());
        logger.info("Fecha Inicio Timbrado: {}", DE.getgTimb().getdFeIniT());
        
        // Logs de Condiciones
        logger.info("=== CONDICIONES ===");
        logger.info("Condición de Operación: {}", DE.getgDtipDE().getgCamCond().getiCondOpe());
        logger.info("Indicador de Presencia: {}", DE.getgDtipDE().getgCamFE().getiIndPres());
        logger.info("Tipo de Transacción: {}", DE.getgDatGralOpe().getgOpeCom().getiTipTra());
        
        // Logs de Items
        logger.info("=== ITEMS ===");
        logger.info("Cantidad de items: {}", DE.getgDtipDE().getgCamItemList().size());
        for (int i = 0; i < DE.getgDtipDE().getgCamItemList().size(); i++) {
            TgCamItem item = DE.getgDtipDE().getgCamItemList().get(i);
            logger.info("Item {}: Código={}, Descripción={}, Cantidad={}, Precio={}", 
                i + 1, 
                item.getdCodInt(), 
                item.getdDesProSer(), 
                item.getdCantProSer(), 
                item.getgValorItem().getdPUniProSer());
        }
        
        logger.info("=== APLICANDO FIX FINAL DE TOTALES (justo antes de envío) ===");
        
        // CRÍTICO: Aplicar fix de totales IVA justo antes de enviar
        // La librería regenera estos valores en setupSOAPElements(), así que debemos
        // forzarlos nuevamente justo antes del envío
        TgTotSub totalesFinales = DE.getgTotSub();
        logger.info("Totales ANTES del fix final - dIVA10: {}, dLiqTotIVA10: {}", 
            totalesFinales.getdIVA10(), totalesFinales.getdLiqTotIVA10());
        
        aplicarFixTotalesIVA(totalesFinales);
        
        logger.info("Totales DESPUÉS del fix final - dIVA10: {}, dLiqTotIVA10: {}", 
            totalesFinales.getdIVA10(), totalesFinales.getdLiqTotIVA10());
        
        logger.info("=== ENVIANDO A SIFEN ===");
        logger.info("NOTA: Usando servicio ASÍNCRONO (recepcionLoteDE) porque el RUC no está habilitado para servicio síncrono");

        // Enviar a SIFEN usando servicio ASÍNCRONO (recepcionLoteDE)
        // El error 1264 indica que el RUC no tiene permiso para servicio síncrono (recepcionDE)
        List<DocumentoElectronico> loteDeUno = Arrays.asList(DE);
        RespuestaRecepcionLoteDE retLote = Sifen.recepcionLoteDE(loteDeUno);
        
        logger.info("=== RESPUESTA DE SIFEN (LOTE) ===");
        logger.info("Código HTTP: {}", retLote.getCodigoEstado());
        logger.info("Código de respuesta LOTE: {}", retLote.getdCodRes());
        logger.info("Mensaje de respuesta LOTE: {}", retLote.getdMsgRes());
        
        // Crear objeto RespuestaRecepcionDE compatible para guardar en BD
        RespuestaRecepcionDE ret = new RespuestaRecepcionDE();
        ret.setCodigoEstado(retLote.getCodigoEstado());
        ret.setdCodRes(retLote.getdCodRes());
        ret.setdMsgRes(retLote.getdMsgRes());
        ret.setRespuestaBruta(retLote.getRespuestaBruta());
        
        logger.info("=== RESPUESTA DE SIFEN (adaptada para DE individual) ===");
        logger.info("Código HTTP: {}", ret.getCodigoEstado());
        logger.info("Código de respuesta: {}", ret.getdCodRes());
        logger.info("Mensaje de respuesta: {}", ret.getdMsgRes());
        
        // Si los campos están null, intentar obtener de la respuesta bruta
        if (ret.getdCodRes() == null && ret.getRespuestaBruta() != null) {
            logger.warn("⚠️ Códigos null - Parseando respuesta bruta...");
            logger.info("Respuesta bruta SIFEN: {}", ret.getRespuestaBruta());
            
            // Intentar extraer información de la respuesta XML
            String respuestaBruta = ret.getRespuestaBruta();
            if (respuestaBruta.contains("dCodRes")) {
                int startCod = respuestaBruta.indexOf("<ns2:dCodRes>") + 13;
                int endCod = respuestaBruta.indexOf("</ns2:dCodRes>");
                if (startCod > 13 && endCod > startCod) {
                    String codigo = respuestaBruta.substring(startCod, endCod);
                    logger.info("Código extraído de XML: {}", codigo);
                }
            }
            if (respuestaBruta.contains("dMsgRes")) {
                int startMsg = respuestaBruta.indexOf("<ns2:dMsgRes>") + 13;
                int endMsg = respuestaBruta.indexOf("</ns2:dMsgRes>");
                if (startMsg > 13 && endMsg > startMsg) {
                    String mensaje = respuestaBruta.substring(startMsg, endMsg);
                    logger.info("Mensaje extraído de XML: {}", mensaje);
                }
            }
            if (respuestaBruta.contains("dEstRes")) {
                int startEst = respuestaBruta.indexOf("<ns2:dEstRes>") + 13;
                int endEst = respuestaBruta.indexOf("</ns2:dEstRes>");
                if (startEst > 13 && endEst > startEst) {
                    String estado = respuestaBruta.substring(startEst, endEst);
                    logger.info("Estado extraído de XML: {}", estado);
                }
            }
        } else if (ret.getdCodRes() != null) {
            switch (ret.getdCodRes()) {
                case "0260":
                    logger.info("✅ DE APROBADO - Recepción y aprobación del DE");
                    break;
                case "0300":
                    logger.info("✅ LOTE RECIBIDO - El DE fue recibido en el lote");
                    break;
                case "1264":
                    logger.error("❌ ERROR 1264 - RUC del emisor no está habilitado para utilizar este tipo de servicio");
                    logger.error("   Solución: Verificar que el RUC {} esté habilitado en SIFEN para facturación electrónica", 
                        DE.getgDatGralOpe().getgEmis().getdRucEm() + "-" + DE.getgDatGralOpe().getgEmis().getdDVEmi());
                    break;
                default:
                    logger.warn("⚠️ Código de respuesta: {} - {}", ret.getdCodRes(), ret.getdMsgRes());
            }
        }
        
        logger.info("Respuesta toString(): {}", ret.toString());
        
        // Guardar el LoteDE y el DocumentoElectronico en la base de datos
        guardarLoteYDocumento(factura, DE, ret, retLote);
        
        logger.info("=== TEST DE RECEPCIÓN DE DE INDIVIDUAL CON DATOS REALES COMPLETADO ===");
    }

    @Test
    @Transactional
    @Commit
    public void testConsultaDE() throws SifenException {
        logger.info("=== INICIANDO TEST DE CONSULTA DE DE ===");
        
        String cdcParaConsultar = "01800805534001002000000722021040613265708133"; // <--- CAMBIAR ESTE CDC
        
        RespuestaConsultaDE ret = Sifen.consultaDE(cdcParaConsultar);
        logger.info("Respuesta de consulta DE: {}", ret.toString());
        
        logger.info("=== TEST DE CONSULTA DE DE COMPLETADO ===");
    }

    @Test
    @Transactional
    @Commit
    public void testRecepcionLoteDE() throws SifenException {
        logger.info("=== INICIANDO TEST DE RECEPCIÓN DE LOTE DE ===");

        DocumentoElectronico de = setupDocumentoElectronico();

        TgCamItem tgCamItem00 = createTgCamItem(
                "001",
                "Servicio de Liquidación de IVA",
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(120000),
                TcUniMed.UNI,
                BigDecimal.valueOf(0)
        );

        TgCamItem tgCamItem01 = createTgCamItem(
                "002",
                "Servicio de Liquidación de IRP",
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(88000),
                TcUniMed.UNI,
                BigDecimal.valueOf(1.3)
        );

        TgDtipDE tgDtipDE = de.getgDtipDE();
        tgDtipDE.getgCamItemList().add(tgCamItem00);
        tgDtipDE.getgCamItemList().add(tgCamItem01);

        RespuestaRecepcionLoteDE ret = Sifen.recepcionLoteDE(Collections.singletonList(de));
        
        logger.info("Código de estado HTTP: {}", ret.getCodigoEstado());
        logger.info("Código de respuesta SIFEN: {}", ret.getdCodRes());
        logger.info("Mensaje de respuesta SIFEN: {}", ret.getdMsgRes());
        logger.info("Respuesta completa: {}", ret.toString());
        
        assertEquals(200, ret.getCodigoEstado());
        assertEquals("0300", ret.getdCodRes());
        assertEquals("Lote recibido con éxito", ret.getdMsgRes());
        
        logger.info("=== TEST DE RECEPCIÓN DE LOTE DE COMPLETADO ===");
    }

    @Test
    @Transactional
    @Commit
    public void testConsultaLoteDe() throws SifenException {
        logger.info("=== INICIANDO TEST DE CONSULTA DE LOTE DE ===");
        
        String protocoloLote = "1078608211748102351"; // <--- CAMBIAR ESTE PROTOCOLO
        
        RespuestaConsultaLoteDE ret = Sifen.consultaLoteDE(protocoloLote);
        logger.info("Respuesta de consulta lote DE: {}", ret.getRespuestaBruta().toString());
        
        logger.info("=== TEST DE CONSULTA DE LOTE DE COMPLETADO ===");
    }

    @Test
    @Transactional
    @Commit
    public void testRecepcionEvento() throws SifenException {
        logger.info("=== INICIANDO TEST DE RECEPCIÓN DE EVENTO ===");
        
        LocalDateTime currentDate = LocalDateTime.now();

        // Evento de Disconformidad
        TrGeVeDisconf trGeVeDisconf = new TrGeVeDisconf();
        trGeVeDisconf.setId("01800805534001002000000722021040613265708133"); // <--- CAMBIAR ESTE CDC
        trGeVeDisconf.setmOtEve("Prueba de disconformidad de documento electrónico");

        TgGroupTiEvt tgGroupTiEvt = new TgGroupTiEvt();
        tgGroupTiEvt.setrGeVeDisconf(trGeVeDisconf);

        TrGesEve rGesEve1 = new TrGesEve();
        rGesEve1.setId("1");
        rGesEve1.setdFecFirma(currentDate);
        rGesEve1.setgGroupTiEvt(tgGroupTiEvt);

        EventosDE eventosDE = new EventosDE();
        eventosDE.setrGesEveList(Collections.singletonList(rGesEve1));

        RespuestaRecepcionEvento ret = Sifen.recepcionEvento(eventosDE);
        logger.info("Respuesta de recepción evento: {}", ret.toString());
        
        logger.info("=== TEST DE RECEPCIÓN DE EVENTO COMPLETADO ===");
    }

    private DocumentoElectronico setupDocumentoElectronico() {
        LocalDateTime currentDate = LocalDateTime.now();

        // Grupo A
        DocumentoElectronico de = new DocumentoElectronico();
        de.setdFecFirma(currentDate);
        de.setdSisFact((short) 1);

        // Grupo B
        TgOpeDE gOpeDE = new TgOpeDE();
        gOpeDE.setiTipEmi(TTipEmi.NORMAL);
        de.setgOpeDE(gOpeDE);

        // Grupo C
        TgTimb gTimb = new TgTimb();
        gTimb.setiTiDE(TTiDE.FACTURA_ELECTRONICA);
        gTimb.setdNumTim(12557662);
        gTimb.setdEst("001");
        gTimb.setdPunExp("002");
        gTimb.setdNumDoc("0000008");
        gTimb.setdFeIniT(LocalDate.parse("2019-07-31"));
        de.setgTimb(gTimb);

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

        TgDatRec gDatRec = new TgDatRec();
        gDatRec.setiNatRec(TiNatRec.NO_CONTRIBUYENTE);
        gDatRec.setiTiOpe(TiTiOpe.B2C);
        gDatRec.setcPaisRec(PaisType.PRY);
        gDatRec.setiTipIDRec(TiTipDocRec.CEDULA_PARAGUAYA);
        gDatRec.setdNumIDRec("4579993");
        gDatRec.setdNomRec("Martin Zarza");
        dDatGralOpe.setgDatRec(gDatRec);
        de.setgDatGralOpe(dDatGralOpe);

        // Grupo E
        TgDtipDE gDtipDE = new TgDtipDE();

        TgCamFE gCamFE = new TgCamFE();
        gCamFE.setiIndPres(TiIndPres.OPERACION_ELECTRONICA);
        gDtipDE.setgCamFE(gCamFE);

        TgCamCond gCamCond = new TgCamCond();
        gCamCond.setiCondOpe(TiCondOpe.CREDITO);

        TgPagCred gPagCred = new TgPagCred();
        gPagCred.setiCondCred(TiCondCred.PLAZO);
        gPagCred.setdPlazoCre("60 días");

        gCamCond.setgPagCred(gPagCred);
        gDtipDE.setgCamCond(gCamCond);

        gDtipDE.setgCamItemList(new ArrayList<>());
        de.setgDtipDE(gDtipDE);

        // Grupo F
        de.setgTotSub(new TgTotSub());

        return de;
    }

    private TgCamItem createTgCamItem(
            String codigo,
            String descripcion,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            TcUniMed uniMed,
            BigDecimal pctDescuento
    ) {
        TgCamItem gCamItem = new TgCamItem();

        gCamItem.setdCodInt(codigo);
        gCamItem.setdDesProSer(descripcion);
        gCamItem.setcUniMed(uniMed);
        gCamItem.setdCantProSer(cantidad);

        TgValorItem gValorItem = new TgValorItem();
        gValorItem.setdPUniProSer(precioUnitario);

        TgValorRestaItem gValorRestaItem = new TgValorRestaItem();
        gValorItem.setgValorRestaItem(gValorRestaItem);
        gValorRestaItem.setdDescItem(pctDescuento);
        gCamItem.setgValorItem(gValorItem);

        TgCamIVA gCamIVA = new TgCamIVA();
        gCamIVA.setiAfecIVA(TiAfecIVA.GRAVADO);
        gCamIVA.setdPropIVA(BigDecimal.valueOf(100));
        gCamIVA.setdTasaIVA(BigDecimal.valueOf(10));
        gCamItem.setgCamIVA(gCamIVA);

        return gCamItem;
    }
    
    /**
     * Genera un DocumentoElectronico (de SIFEN) a partir de una FacturaLegal de nuestro sistema.
     * Utiliza los datos reales de la factura, timbrado y cliente.
     */
    private DocumentoElectronico generarDEDesdeFactura(FacturaLegal factura) throws Exception {
        logger.info("Generando DE desde FacturaLegal ID: {}", factura.getId());
        
        LocalDateTime fechaFactura = factura.getFecha();
        
        // Grupo A
        DocumentoElectronico DE = new DocumentoElectronico();
        DE.setdFecFirma(fechaFactura);
        DE.setdSisFact((short) 1);

        // Grupo B
        TgOpeDE gOpeDE = new TgOpeDE();
        gOpeDE.setiTipEmi(TTipEmi.NORMAL);
        DE.setgOpeDE(gOpeDE);

        // Grupo C - Datos del Timbrado
        TgTimb gTimb = new TgTimb();
        gTimb.setiTiDE(TTiDE.FACTURA_ELECTRONICA);
        // Datos reales del timbrado de la factura
        gTimb.setdNumTim(Integer.parseInt(factura.getTimbradoDetalle().getTimbrado().getNumero())); // 18270044
        gTimb.setdEst("001"); // Establecimiento fijo por ahora
        gTimb.setdPunExp(factura.getTimbradoDetalle().getPuntoExpedicion()); // 001
        gTimb.setdNumDoc(String.format("%07d", factura.getNumeroFactura())); // 0000023
        gTimb.setdFeIniT(factura.getTimbradoDetalle().getTimbrado().getFechaInicio().toLocalDate());
        DE.setgTimb(gTimb);

        // Grupo D - Datos generales
        TdDatGralOpe dDatGralOpe = new TdDatGralOpe();
        dDatGralOpe.setdFeEmiDE(fechaFactura);

        TgOpeCom gOpeCom = new TgOpeCom();
        gOpeCom.setiTipTra(TTipTra.VENTA_MERCADERIA); // Venta de mercadería (whisky)
        gOpeCom.setiTImp(TTImp.IVA);
        gOpeCom.setcMoneOpe(CMondT.PYG);
        dDatGralOpe.setgOpeCom(gOpeCom);

        // Datos del Emisor (desde el timbrado)
        TgEmis gEmis = new TgEmis();
        String rucCompleto = factura.getTimbradoDetalle().getTimbrado().getRuc(); // "80099482-5"
        String[] rucPartes = rucCompleto.split("-");
        gEmis.setdRucEm(rucPartes[0]); // "80099482"
        gEmis.setdDVEmi(rucPartes.length > 1 ? rucPartes[1] : ""); // "5"
        gEmis.setiTipCont(TiTipCont.PERSONA_JURIDICA);
        gEmis.setdNomEmi(factura.getTimbradoDetalle().getTimbrado().getRazonSocial()); // "FRANCO AREVALOS S.A."
        gEmis.setdDirEmi(factura.getTimbradoDetalle().getDireccion() != null ? 
            factura.getTimbradoDetalle().getDireccion() : "SALTO DEL GUAIRA");
        // CORREGIDO: dNumCas no puede ser "S/N", usar un número válido o omitir
        // gEmis.setdNumCas("S/N"); // ❌ VALOR INVÁLIDO - causa error 0160
        gEmis.setdNumCas("0"); // ✅ Valor válido para cuando no hay número de casa
        
        // CORREGIDO: Usar exactamente los mismos códigos que SifenService.java (producción)
        // Esto evita inconsistencias entre test y producción
        gEmis.setcDepEmi(TDepartamento.CANINDEYU); // Departamento: CANINDEYU (18)
        // NOTA: SifenService NO usa cDisEmi, solo departamento y ciudad
        // gEmis.setcDisEmi((short) 207); // ❌ NO usar - no está en producción
        
        // Código de ciudad (usar el mismo que SifenService)
        gEmis.setcCiuEmi(CodigosGeograficos.Ciudad.SALTO_DEL_GUAIRA.getCodigo()); // Ciudad: SALTO DEL GUAIRA (207)
        gEmis.setdDesCiuEmi("SALTO DEL GUAIRA"); // Nombre de la ciudad
        // gEmis.setcDisEmi(0);
        gEmis.setdTelEmi(factura.getTimbradoDetalle().getTelefono() != null && !factura.getTimbradoDetalle().getTelefono().isEmpty() ? 
            factura.getTimbradoDetalle().getTelefono() : "021000000"); // Teléfono por defecto si no está disponible
        gEmis.setdEmailE(factura.getTimbradoDetalle().getTimbrado().getEmail()); // "francoarevalos05@gmail.com"

        // Actividades económicas - SIFEN requiere al menos una
        List<TgActEco> gActEcoList = new ArrayList<>();
        
        // CORREGIDO: Usar códigos oficiales del RUC (error 1261 - código incorrecto)
        // Actividad principal - COMERCIO AL POR MAYOR DE BEBIDAS
        TgActEco gActEco = new TgActEco();
        gActEco.setcActEco("46304"); // C4_46304 - COMERCIO AL POR MAYOR DE BEBIDAS
        gActEco.setdDesActEco("COMERCIO AL POR MAYOR DE BEBIDAS");
        gActEcoList.add(gActEco);
        
        // Actividad secundaria - COMERCIO AL POR MENOR EN MINI MERCADOS Y DESPENSAS
        TgActEco gActEco2 = new TgActEco();
        gActEco2.setcActEco("47112"); // C4_47112 - COMERCIO AL POR MENOR EN MINI MERCADOS Y DESPENSAS
        gActEco2.setdDesActEco("COMERCIO AL POR MENOR EN MINI MERCADOS Y DESPENSAS");
        gActEcoList.add(gActEco2);
        
        gEmis.setgActEcoList(gActEcoList);
        
        dDatGralOpe.setgEmis(gEmis);

        // Datos del Receptor (Cliente) - Cliente ID 194 (contribuyente) hardcodeado
        TgDatRec gDatRec = new TgDatRec();
        
        // Cliente contribuyente
        gDatRec.setiNatRec(TiNatRec.CONTRIBUYENTE);
        gDatRec.setiTiOpe(TiTiOpe.B2C);
        gDatRec.setcPaisRec(PaisType.PRY);
        
        // Tipo de contribuyente: Persona Física (es un RUC de persona física)
        gDatRec.setiTiContRec(TiTipCont.PERSONA_FISICA);
        
        // Datos del cliente ID 194 - CONTRIBUYENTE paraguayo
        // IMPORTANTE: Para contribuyente, usar CEDULA_PARAGUAYA y setear TANTO dNumIDRec como dRucRec
        gDatRec.setiTipIDRec(TiTipDocRec.CEDULA_PARAGUAYA);
        
        // CORREGIDO: Calcular dígito verificador correctamente usando la clase existente
        String rucSinDV = "5364471"; // RUC del cliente 194 sin DV
        int dvInt = CalcularVerificadorRuc.getDigitoVerificador(rucSinDV);
        String dvCalculado = String.valueOf(dvInt);
        logger.info("🔢 Cliente 194 - RUC sin DV: '{}', DV calculado: '{}'", rucSinDV, dvCalculado);
        
        gDatRec.setdNumIDRec(rucSinDV); // Número de cédula (genera <dNumIDRec>)
        gDatRec.setdRucRec(rucSinDV); // RUC sin DV (genera <dRucRec>) - DEBE SER IGUAL a dNumIDRec
        gDatRec.setdDVRec(Short.parseShort(dvCalculado)); // Dígito verificador calculado correctamente
        gDatRec.setdNomRec("PERALTA CRISTIAN RAFAEL"); // Razón social
        
        // Campos opcionales (comentados por ahora para probar igual que el test original)
        // gDatRec.setdDirRec("BARRIO 15 DE AGOSTO");
        // gDatRec.setdTelRec("0971276602");
        // gDatRec.setdEmailRec("CRISRPERALTA69@GMAIL.COM");
        
        dDatGralOpe.setgDatRec(gDatRec);
        DE.setgDatGralOpe(dDatGralOpe);

        // Grupo E - Items y condiciones
        TgDtipDE gDtipDE = new TgDtipDE();

        TgCamFE gCamFE = new TgCamFE();
        // Usar OPERACION_ELECTRONICA igual que el test original
        gCamFE.setiIndPres(TiIndPres.OPERACION_ELECTRONICA);
        gDtipDE.setgCamFE(gCamFE);

        TgCamCond gCamCond = new TgCamCond();
        
        // Configurar según el tipo de venta: CONTADO o CREDITO
        boolean esCredito = factura.getCredito() != null && factura.getCredito();
        gCamCond.setiCondOpe(esCredito ? TiCondOpe.CREDITO : TiCondOpe.CONTADO);

        List<TgPaConEIni> gPaConEIniList = new ArrayList<>();
        TgPaConEIni gPaConEIni = new TgPaConEIni();
        gPaConEIni.setiTiPago(TiTiPago.EFECTIVO);
        gPaConEIni.setcMoneTiPag(CMondT.PYG);
        
        // CORREGIDO: Agregar monto obligatorio para operaciones al contado
        if (esCredito) {
            // Para crédito: monto de entrega inicial (puede ser 0)
            gPaConEIni.setdMonTiPag(BigDecimal.valueOf(0));
        } else {
            // Para contado: monto total de la operación
            gPaConEIni.setdMonTiPag(BigDecimal.valueOf(19000)); // Monto total en efectivo
        }
        
        gPaConEIniList.add(gPaConEIni);
        gCamCond.setgPaConEIniList(gPaConEIniList);
        
        // CORREGIDO: Configurar forma de pago obligatoria para evitar error SIFEN
        // Usar la misma lógica que SifenService.java (producción)
        if (esCredito) {
            // Para crédito: configurar condiciones de crédito
            TgPagCred gPagCred = new TgPagCred();
            gPagCred.setiCondCred(TiCondCred.PLAZO);
            gPagCred.setdPlazoCre("30 días");
            gCamCond.setgPagCred(gPagCred);
        } 
        
        gDtipDE.setgCamCond(gCamCond);

        // Cargar items de la factura
        List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(factura.getId());
        List<TgCamItem> gCamItemList = new ArrayList<>();
        
        for (int i = 0; i < items.size(); i++) {
            FacturaLegalItem item = items.get(i);
            TgCamItem gCamItem = new TgCamItem();
            gCamItem.setdCodInt(String.format("%03d", i + 1)); // 001, 002, etc.
            gCamItem.setdDesProSer(item.getDescripcion()); // "TRES LEONES ETIQUETA NEGRA 750ML"
            gCamItem.setcUniMed(TcUniMed.UNI);
            gCamItem.setdCantProSer(BigDecimal.valueOf(item.getCantidad().doubleValue())); // Convertir a BigDecimal

            TgValorItem gValorItem = new TgValorItem();
            gValorItem.setdPUniProSer(BigDecimal.valueOf(item.getPrecioUnitario().doubleValue())); // Convertir a BigDecimal

            TgValorRestaItem gValorRestaItem = new TgValorRestaItem();
            gValorItem.setgValorRestaItem(gValorRestaItem);
            gCamItem.setgValorItem(gValorItem);

            TgCamIVA gCamIVA = new TgCamIVA();
            gCamIVA.setiAfecIVA(TiAfecIVA.GRAVADO);
            gCamIVA.setdPropIVA(BigDecimal.valueOf(100));
            gCamIVA.setdTasaIVA(BigDecimal.valueOf(10)); // IVA 10%
            gCamItem.setgCamIVA(gCamIVA);

            gCamItemList.add(gCamItem);
        }

        gDtipDE.setgCamItemList(gCamItemList);
        DE.setgDtipDE(gDtipDE);

        // Grupo F - Totales
        DE.setgTotSub(new TgTotSub());
        
        // WORKAROUND: Bug conocido en rshk-jsifenlib 0.2.4
        // La librería genera dLiqTotIVA10=0 cuando debería ser igual a dIVA10
        // TgTotSub no permite setear manualmente estos valores (son calculados automáticamente)
        // Este bug causa rechazo en SIFEN con código 1264
        // Solución temporal: Usar reflection para forzar los valores correctos
        // TODO: Reportar issue a https://github.com/roshkadev/rshk-jsifenlib/issues
        
        TgTotSub totales = DE.getgTotSub();
        logger.info("Totales generados ANTES del fix - dIVA10: {}, dLiqTotIVA10: {}", 
            totales.getdIVA10(), totales.getdLiqTotIVA10());
        logger.info("Totales generados ANTES del fix - dIVA5: {}, dLiqTotIVA5: {}", 
            totales.getdIVA5(), totales.getdLiqTotIVA5());
        
        // Aplicar workaround con reflection
        aplicarFixTotalesIVA(totales);
        
        logger.info("Totales generados DESPUÉS del fix - dIVA10: {}, dLiqTotIVA10: {}", 
            totales.getdIVA10(), totales.getdLiqTotIVA10());
        logger.info("Totales generados DESPUÉS del fix - dIVA5: {}, dLiqTotIVA5: {}", 
            totales.getdIVA5(), totales.getdLiqTotIVA5());
        
        logger.info("DE generado correctamente con {} items", items.size());
        return DE;
    }
    
    /**
     * Genera un DocumentoElectronico de SIFEN desde una FacturaLegal usando datos reales del proyecto
     * 
     * @param factura FacturaLegal con datos reales
     * @return DocumentoElectronico configurado para SIFEN
     * @throws Exception Si hay error en la configuración
     */
    private com.roshka.sifen.core.beans.DocumentoElectronico generarDEDesdeFacturaDatosReales(FacturaLegal factura) throws Exception {
        // VALIDACIONES EXHAUSTIVAS DE DATOS REQUERIDOS
        logger.info("=== VALIDANDO DATOS REQUERIDOS PARA GENERACIÓN DE DE ===");
        
        // Validar factura
        if (factura == null) {
            throw new IllegalArgumentException("factura es requerido");
        }
        
        // Validar timbradoDetalle
        if (factura.getTimbradoDetalle() == null) {
            throw new IllegalArgumentException("factura.timbradoDetalle es requerido");
        }
        
        // Validar timbrado
        if (factura.getTimbradoDetalle().getTimbrado() == null) {
            throw new IllegalArgumentException("timbradoDetalle.timbrado es requerido");
        }
        
        // Validar sucursal
        if (factura.getTimbradoDetalle().getSucursal() == null) {
            throw new IllegalArgumentException("timbradoDetalle.sucursal es requerido");
        }
        
        LocalDateTime currentDate = LocalDateTime.now();

        // Grupo A - Identificación del DE
        com.roshka.sifen.core.beans.DocumentoElectronico DE = new com.roshka.sifen.core.beans.DocumentoElectronico();
        DE.setdFecFirma(currentDate);
        DE.setdSisFact((short) 1);

        // Grupo B - Operación del DE
        TgOpeDE gOpeDE = new TgOpeDE();
        gOpeDE.setiTipEmi(TTipEmi.NORMAL);
        DE.setgOpeDE(gOpeDE);

        // Grupo C - Timbrado
        TgTimb gTimb = new TgTimb();
        gTimb.setiTiDE(TTiDE.FACTURA_ELECTRONICA);
        
        // Validar número de timbrado
        if (factura.getTimbradoDetalle().getTimbrado().getNumero() == null) {
            throw new IllegalArgumentException("timbrado.numero es requerido");
        }
        gTimb.setdNumTim(Integer.parseInt(factura.getTimbradoDetalle().getTimbrado().getNumero()));
        
        // CORREGIDO: Usar código fijo que funciona (igual al método que funciona)
        gTimb.setdEst("001"); // Establecimiento fijo - igual al método que funciona
        
        // Validar punto de expedición
        if (factura.getTimbradoDetalle().getPuntoExpedicion() == null) {
            throw new IllegalArgumentException("timbradoDetalle.puntoExpedicion es requerido");
        }
        gTimb.setdPunExp(factura.getTimbradoDetalle().getPuntoExpedicion());
        
        // Validar número de factura
        if (factura.getNumeroFactura() == null) {
            throw new IllegalArgumentException("factura.numeroFactura es requerido");
        }
        gTimb.setdNumDoc(String.format("%07d", factura.getNumeroFactura()));
        
        // Validar fecha de inicio del timbrado
        if (factura.getTimbradoDetalle().getTimbrado().getFechaInicio() == null) {
            throw new IllegalArgumentException("timbrado.fechaInicio es requerido");
        }
        gTimb.setdFeIniT(factura.getTimbradoDetalle().getTimbrado().getFechaInicio().toLocalDate());
        DE.setgTimb(gTimb);

        // Grupo D - Datos Generales de la Operación
        TdDatGralOpe dDatGralOpe = new TdDatGralOpe();
        dDatGralOpe.setdFeEmiDE(factura.getFecha());

        TgOpeCom gOpeCom = new TgOpeCom();
        gOpeCom.setiTipTra(TTipTra.VENTA_MERCADERIA); // Por ahora siempre venta mercadería
        gOpeCom.setiTImp(TTImp.IVA);
        gOpeCom.setcMoneOpe(CMondT.PYG);
        dDatGralOpe.setgOpeCom(gOpeCom);

        // Datos del Emisor (desde TimbradoDetalle y Timbrado)
        TgEmis gEmis = new TgEmis();
        
        // Validar RUC del emisor
        if (factura.getTimbradoDetalle().getTimbrado().getRuc() == null) {
            throw new IllegalArgumentException("timbrado.ruc es requerido");
        }
        String rucCompleto = factura.getTimbradoDetalle().getTimbrado().getRuc();
        String[] rucPartes = rucCompleto.split("-");
        gEmis.setdRucEm(rucPartes[0]);
        gEmis.setdDVEmi(rucPartes.length > 1 ? rucPartes[1] : "");
        gEmis.setiTipCont(TiTipCont.PERSONA_JURIDICA);
        
        // Validar razón social del emisor
        if (factura.getTimbradoDetalle().getTimbrado().getRazonSocial() == null) {
            throw new IllegalArgumentException("timbrado.razonSocial es requerido");
        }
        gEmis.setdNomEmi(factura.getTimbradoDetalle().getTimbrado().getRazonSocial());
        
        // Validar dirección del emisor
        if (factura.getTimbradoDetalle().getDireccion() == null) {
            throw new IllegalArgumentException("timbradoDetalle.direccion es requerido");
        }
        gEmis.setdDirEmi(factura.getTimbradoDetalle().getDireccion());
        gEmis.setdNumCas("0"); // Dejar en 0 por el momento
        
        // Validar teléfono del emisor
        if (factura.getTimbradoDetalle().getTelefono() == null) {
            throw new IllegalArgumentException("timbradoDetalle.telefono es requerido");
        }
        gEmis.setdTelEmi(factura.getTimbradoDetalle().getTelefono());
        
        // Validar email del emisor
        if (factura.getTimbradoDetalle().getTimbrado().getEmail() == null) {
            throw new IllegalArgumentException("timbrado.email es requerido");
        }
        gEmis.setdEmailE(factura.getTimbradoDetalle().getTimbrado().getEmail());
        
        // Validar códigos geográficos de TimbradoDetalle
        if (factura.getTimbradoDetalle().getDepartamento() == null) {
            throw new IllegalArgumentException("timbradoDetalle.departamento es requerido");
        }
        TDepartamento tdep = mapearDepartamento(factura.getTimbradoDetalle().getDepartamento());
        gEmis.setcDepEmi(tdep);
        
        if (factura.getTimbradoDetalle().getCodigoCiudad() == null) {
            throw new IllegalArgumentException("timbradoDetalle.codigoCiudad es requerido");
        }
        Integer codigoCiudad = Integer.parseInt(factura.getTimbradoDetalle().getCodigoCiudad());
        gEmis.setcCiuEmi(codigoCiudad);
        
        if (factura.getTimbradoDetalle().getCiudad() == null) {
            throw new IllegalArgumentException("timbradoDetalle.ciudad es requerido");
        }
        gEmis.setdDesCiuEmi(factura.getTimbradoDetalle().getCiudad());

        // Validar actividades económicas desde Timbrado
        List<TgActEco> gActEcoList = new ArrayList<>();
        
        // Validar actividad principal
        if (factura.getTimbradoDetalle().getTimbrado().getCodActividadEconomicaPrincipal() == null) {
            throw new IllegalArgumentException("timbrado.codActividadEconomicaPrincipal es requerido");
        }
        TgActEco gActEco = new TgActEco();
        gActEco.setcActEco(factura.getTimbradoDetalle().getTimbrado().getCodActividadEconomicaPrincipal());
        
        if (factura.getTimbradoDetalle().getTimbrado().getDescActividadEconomicaPrincipal() == null) {
            throw new IllegalArgumentException("timbrado.descActividadEconomicaPrincipal es requerido");
        }
        gActEco.setdDesActEco(factura.getTimbradoDetalle().getTimbrado().getDescActividadEconomicaPrincipal());
        gActEcoList.add(gActEco);
        
        // Validar actividades secundarias (separadas por coma)
        if (factura.getTimbradoDetalle().getTimbrado().getListCodigoActividadEconomicaSecundaria() == null) {
            throw new IllegalArgumentException("timbrado.listCodigoActividadEconomicaSecundaria es requerido");
        }
        
        if (factura.getTimbradoDetalle().getTimbrado().getListDescripcionActividadEconomicaSecundaria() == null) {
            throw new IllegalArgumentException("timbrado.listDescripcionActividadEconomicaSecundaria es requerido");
        }
        
        String[] codigosSecundarios = factura.getTimbradoDetalle().getTimbrado().getListCodigoActividadEconomicaSecundaria().split(",");
        String[] descripcionesSecundarias = factura.getTimbradoDetalle().getTimbrado().getListDescripcionActividadEconomicaSecundaria().split(",");
        
        for (int i = 0; i < codigosSecundarios.length && i < descripcionesSecundarias.length; i++) {
            TgActEco gActEcoSec = new TgActEco();
            gActEcoSec.setcActEco(codigosSecundarios[i].trim());
            gActEcoSec.setdDesActEco(descripcionesSecundarias[i].trim());
            gActEcoList.add(gActEcoSec);
        }
        
        gEmis.setgActEcoList(gActEcoList);
        dDatGralOpe.setgEmis(gEmis);

        // Validar datos del Receptor (Cliente)
        if (factura.getCliente() == null) {
            throw new IllegalArgumentException("factura.cliente es requerido");
        }
        
        if (factura.getCliente().getPersona() == null) {
            throw new IllegalArgumentException("cliente.persona es requerido");
        }
        
        TgDatRec gDatRec = new TgDatRec();
        
        // Cliente contribuyente o no contribuyente
        boolean esContribuyente = factura.getCliente().getTributa() != null && factura.getCliente().getTributa();
            
            if (esContribuyente) {
                gDatRec.setiNatRec(TiNatRec.CONTRIBUYENTE);
                gDatRec.setcPaisRec(PaisType.PRY);
                
                // CORREGIDO: Determinar tipo de contribuyente dinámicamente
                TiTipCont tipoContribuyente = determinarTipoContribuyente(factura.getCliente());
                gDatRec.setiTiContRec(tipoContribuyente);
                
                // CORREGIDO: setiTiOpe depende del tipo de contribuyente
                // Persona física = B2C, Persona jurídica (empresa) = B2B
                if (tipoContribuyente == TiTipCont.PERSONA_FISICA) {
                    gDatRec.setiTiOpe(TiTiOpe.B2C);
                } else {
                    gDatRec.setiTiOpe(TiTiOpe.B2B);
                }
                
                // Validar datos del documento
                if (factura.getCliente().getPersona().getDocumento() == null) {
                    throw new IllegalArgumentException("persona.documento es requerido");
                }
                String documentoCompleto = factura.getCliente().getPersona().getDocumento();
                logger.info("📋 Documento completo del cliente: '{}'", documentoCompleto);
                
                String documentoSinDv = documentoCompleto != null && documentoCompleto.contains("-") ? documentoCompleto.split("-")[0] : documentoCompleto;
                logger.info("📋 Documento sin DV extraído: '{}'", documentoSinDv);
                
                int dvInt = CalcularVerificadorRuc.getDigitoVerificador(documentoSinDv);
                String dv = String.valueOf(dvInt);
                logger.info("📋 Dígito verificador calculado: '{}'", dv);
                logger.info("📋 RUC completo para SIFEN: '{}-{}'", documentoSinDv, dv);
                
                gDatRec.setiTipIDRec(TiTipDocRec.CEDULA_PARAGUAYA);
                gDatRec.setdNumIDRec(documentoSinDv);
                gDatRec.setdRucRec(documentoSinDv);
                gDatRec.setdDVRec(Short.parseShort(dv));
                
                // Validar nombre del cliente
                if (factura.getCliente().getPersona().getNombre() == null) {
                    throw new IllegalArgumentException("persona.nombre es requerido");
                }
                gDatRec.setdNomRec(factura.getCliente().getPersona().getNombre());
                
                // Actualizar tipo de contribuyente en BD si era null
                if (factura.getCliente().getTipoContribuyente() == null) {
                    factura.getCliente().setTipoContribuyente(tipoContribuyente == TiTipCont.PERSONA_FISICA ? 1 : 2);
                    clienteService.save(factura.getCliente());
                    logger.info("✅ Cliente actualizado en BD con tipo de contribuyente: {}", tipoContribuyente);
                }
                
            } else {
                // Cliente no contribuyente
                gDatRec.setiNatRec(TiNatRec.NO_CONTRIBUYENTE);
                gDatRec.setiTiOpe(TiTiOpe.B2C);
                gDatRec.setcPaisRec(PaisType.PRY);
                gDatRec.setiTipIDRec(TiTipDocRec.INNOMINADO);
                gDatRec.setdNumIDRec("X");
                gDatRec.setdNomRec(factura.getCliente().getPersona().getNombre() != null ? 
                    factura.getCliente().getPersona().getNombre() : "SIN NOMBRE");
            }
        
        dDatGralOpe.setgDatRec(gDatRec);
        DE.setgDatGralOpe(dDatGralOpe);

        // Grupo E - Items y condiciones
        TgDtipDE gDtipDE = new TgDtipDE();

        TgCamFE gCamFE = new TgCamFE();
        gCamFE.setiIndPres(TiIndPres.OPERACION_PRESENCIAL); // Por ahora siempre presencial
        gDtipDE.setgCamFE(gCamFE);

        TgCamCond gCamCond = new TgCamCond();
        
        // Configurar según el tipo de venta: CONTADO o CREDITO
        boolean esCredito = factura.getCredito() != null && factura.getCredito();
        gCamCond.setiCondOpe(esCredito ? TiCondOpe.CREDITO : TiCondOpe.CONTADO);

        // CORREGIDO: Forma de pago con monto real de la factura
        List<TgPaConEIni> gPaConEIniList = new ArrayList<>();
        TgPaConEIni gPaConEIni = new TgPaConEIni();
        gPaConEIni.setiTiPago(TiTiPago.EFECTIVO); // Por ahora siempre efectivo
        gPaConEIni.setcMoneTiPag(CMondT.PYG);
        
        // Validar total de la factura
        if (factura.getTotalFinal() == null) {
            throw new IllegalArgumentException("factura.totalFinal es requerido");
        }
        BigDecimal montoTotal = BigDecimal.valueOf(factura.getTotalFinal());
        if (esCredito) {
            gPaConEIni.setdMonTiPag(BigDecimal.valueOf(0)); // Entrega inicial para crédito
        } else {
            gPaConEIni.setdMonTiPag(montoTotal); // Monto total para contado
        }
        
        gPaConEIniList.add(gPaConEIni);
        gCamCond.setgPaConEIniList(gPaConEIniList);
        
        if (esCredito) {
            // CORREGIDO: Plazo desde configuración de crédito
            TgPagCred gPagCred = new TgPagCred();
            gPagCred.setiCondCred(TiCondCred.PLAZO);
            gPagCred.setdPlazoCre("30 días"); // Plazo por defecto para créditos
            gCamCond.setgPagCred(gPagCred);
        }
        
        gDtipDE.setgCamCond(gCamCond);

        // Validar items de la factura
        List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(factura.getId());
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("factura.items es requerido - no se encontraron items para la factura");
        }
        
        List<TgCamItem> gCamItemList = new ArrayList<>();
        
        for (int i = 0; i < items.size(); i++) {
            FacturaLegalItem item = items.get(i);
            
            // Validar descripción del item
            if (item.getDescripcion() == null) {
                throw new IllegalArgumentException("item[" + i + "].descripcion es requerido");
            }
            
            // Validar cantidad del item
            if (item.getCantidad() == null) {
                throw new IllegalArgumentException("item[" + i + "].cantidad es requerido");
            }
            
            // Validar precio unitario del item
            if (item.getPrecioUnitario() == null) {
                throw new IllegalArgumentException("item[" + i + "].precioUnitario es requerido");
            }
            
            TgCamItem gCamItem = new TgCamItem();
            gCamItem.setdCodInt(String.format("%03d", i + 1));
            gCamItem.setdDesProSer(item.getDescripcion());
            gCamItem.setcUniMed(TcUniMed.UNI);
            gCamItem.setdCantProSer(BigDecimal.valueOf(item.getCantidad().doubleValue()));

            TgValorItem gValorItem = new TgValorItem();
            gValorItem.setdPUniProSer(BigDecimal.valueOf(item.getPrecioUnitario().doubleValue()));

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

        // Grupo F - Totales
        DE.setgTotSub(new TgTotSub());
        
        // Aplicar workaround para bug de totales IVA
        TgTotSub totales = DE.getgTotSub();
        aplicarFixTotalesIVA(totales);
        
        logger.info("DE generado con datos reales - Cliente: {}, Contribuyente: {}, Items: {}", 
            factura.getCliente() != null ? factura.getCliente().getId() : "N/A",
            factura.getCliente() != null && factura.getCliente().getTributa() != null ? factura.getCliente().getTributa() : false,
            items.size());
        
        logger.info("✅ VALIDACIÓN COMPLETADA - Todos los datos requeridos están presentes");
        logger.info("=== FIN DE VALIDACIÓN ===");
        
        return DE;
    }
    
    /**
     * Determina el tipo de contribuyente del cliente heurísticamente
     * 
     * @param cliente Cliente a analizar
     * @return Tipo de contribuyente determinado
     */
    private TiTipCont determinarTipoContribuyente(Cliente cliente) {
        // Si ya está configurado, usar ese valor
        if (cliente.getTipoContribuyente() != null) {
            return cliente.getTipoContribuyente() == 1 ? TiTipCont.PERSONA_FISICA : TiTipCont.PERSONA_JURIDICA;
        }
        
        // Heurística mejorada: Analizar el documento
        if (cliente.getPersona() != null && cliente.getPersona().getDocumento() != null) {
            String documento = cliente.getPersona().getDocumento();
            
            // Limpiar documento (quitar espacios y guiones)
            String docLimpio = documento.replaceAll("[\\s-]", "");
            
            // Heurística basada en longitud y formato:
            // - Cédula paraguaya: 6-8 dígitos
            // - RUC persona física: 6-8 dígitos + DV
            // - RUC persona jurídica: 7-8 dígitos + DV
            if (docLimpio.matches("\\d{6,8}")) {
                // Documento sin DV, probablemente cédula (persona física)
                return TiTipCont.PERSONA_FISICA;
            } else if (docLimpio.matches("\\d{6,8}\\d")) {
                // Documento con DV, analizar longitud
                String numeroSinDV = docLimpio.substring(0, docLimpio.length() - 1);
                if (numeroSinDV.length() <= 7) {
                    // 6-7 dígitos = persona física
                    return TiTipCont.PERSONA_FISICA;
                } else {
                    // 8 dígitos = persona jurídica
                    return TiTipCont.PERSONA_JURIDICA;
                }
            }
        }
        
        // Por defecto, persona física
        return TiTipCont.PERSONA_FISICA;
    }
    
    
    /**
     * WORKAROUND: Aplica fix temporal para bug de dLiqTotIVA10/dLiqTotIVA5 en rshk-jsifenlib 0.2.4
     * 
     * La librería genera estos campos con valor 0 cuando deberían ser iguales a dIVA10/dIVA5.
     * Como TgTotSub no expone setters para estos campos, usamos reflection para forzar los valores.
     * 
     * @param totales Objeto TgTotSub a corregir
     */
    private void aplicarFixTotalesIVA(TgTotSub totales) {
        try {
            // Fix para IVA 10%
            if (totales.getdIVA10() != null && totales.getdIVA10().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal valorIVA10 = totales.getdIVA10();
                BigDecimal valorActualLiq10 = totales.getdLiqTotIVA10();
                
                if (valorActualLiq10 == null || valorActualLiq10.compareTo(BigDecimal.ZERO) == 0) {
                    Field field = TgTotSub.class.getDeclaredField("dLiqTotIVA10");
                    field.setAccessible(true);
                    field.set(totales, valorIVA10);
                    logger.info("✅ WORKAROUND aplicado: dLiqTotIVA10 corregido de {} a {}", 
                        valorActualLiq10, valorIVA10);
                }
            }
            
            // Fix para IVA 5%
            if (totales.getdIVA5() != null && totales.getdIVA5().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal valorIVA5 = totales.getdIVA5();
                BigDecimal valorActualLiq5 = totales.getdLiqTotIVA5();
                
                if (valorActualLiq5 == null || valorActualLiq5.compareTo(BigDecimal.ZERO) == 0) {
                    Field field = TgTotSub.class.getDeclaredField("dLiqTotIVA5");
                    field.setAccessible(true);
                    field.set(totales, valorIVA5);
                    logger.info("✅ WORKAROUND aplicado: dLiqTotIVA5 corregido de {} a {}", 
                        valorActualLiq5, valorIVA5);
                }
            }
            
            logger.info("✅ Fix de totales IVA aplicado exitosamente");
            
        } catch (NoSuchFieldException e) {
            logger.error("❌ Campo no encontrado en TgTotSub. Versión de librería incompatible?", e);
            logger.error("   Valores NO corregidos. El DE probablemente será rechazado por SIFEN.");
        } catch (IllegalAccessException e) {
            logger.error("❌ No se pudo acceder al campo privado de TgTotSub", e);
            logger.error("   Valores NO corregidos. El DE probablemente será rechazado por SIFEN.");
        } catch (Exception e) {
            logger.error("❌ Error inesperado al aplicar workaround de totales IVA", e);
            logger.error("   Valores NO corregidos. El DE probablemente será rechazado por SIFEN.");
        }
    }
    
    /**
     * Mapea el nombre del departamento a su enum correspondiente.
     */
    private TDepartamento mapearDepartamento(String departamento) {
        if (departamento == null) return TDepartamento.CAPITAL;
        
        switch (departamento.toUpperCase()) {
            case "CAPITAL": return TDepartamento.CAPITAL;
            case "CONCEPCION": case "CONCEPCIÓN": return TDepartamento.CONCEPCION;
            case "SAN PEDRO": return TDepartamento.SAN_PEDRO;
            case "CORDILLERA": return TDepartamento.CORDILLERA;
            case "GUAIRA": case "GUAIRÁ": return TDepartamento.GUAIRA;
            case "CAAGUAZU": case "CAAGUAZÚ": return TDepartamento.CAAGUAZU;
            case "CAAZAPA": case "CAAZAPÁ": return TDepartamento.CAAZAPA;
            case "ITAPUA": case "ITAPÚA": return TDepartamento.ITAPUA;
            case "MISIONES": return TDepartamento.MISIONES;
            case "PARAGUARI": case "PARAGUARÍ": return TDepartamento.PARAGUARI;
            case "ALTO PARANA": case "ALTO PARANÁ": return TDepartamento.ALTO_PARANA;
            case "CENTRAL": return TDepartamento.CENTRAL;
            case "ÑEEMBUCU": case "ÑEEMBUCÚ": return TDepartamento.NEEMBUCU;
            case "AMAMBAY": return TDepartamento.AMAMBAY;
            case "CANINDEYU": case "CANINDEYÚ": return TDepartamento.CANINDEYU;
            case "PRESIDENTE HAYES": case "HAYES": return TDepartamento.PTE_HAYES;
            case "BOQUERON": case "BOQUERÓN": return TDepartamento.BOQUERON;
            case "ALTO PARAGUAY": return TDepartamento.ALTO_PARAGUAY;
            default: return TDepartamento.CAPITAL;
        }
    }
    
    /**
     * Guarda el LoteDE y el DocumentoElectronico en la base de datos después de enviarlo a SIFEN.
     */
    private void guardarLoteYDocumento(FacturaLegal factura, DocumentoElectronico deSifen, 
                                       RespuestaRecepcionDE respuesta, RespuestaRecepcionLoteDE respuestaLote) {
        logger.info("=== GUARDANDO LOTE Y DOCUMENTO EN BASE DE DATOS ===");
        
        try {
            // 1. Crear y guardar el LoteDE
            LoteDE lote = new LoteDE();
            
            // Determinar estado basado en la respuesta
            String codigoRespuesta = respuesta.getdCodRes();
            
            // Si el código es null, intentar extraer de la respuesta bruta
            if (codigoRespuesta == null && respuesta.getRespuestaBruta() != null) {
                String respuestaBruta = respuesta.getRespuestaBruta();
                if (respuestaBruta.contains("dCodRes")) {
                    int startCod = respuestaBruta.indexOf("<ns2:dCodRes>") + 13;
                    int endCod = respuestaBruta.indexOf("</ns2:dCodRes>");
                    if (startCod > 13 && endCod > startCod) {
                        codigoRespuesta = respuestaBruta.substring(startCod, endCod);
                        logger.info("Código extraído de respuesta bruta: {}", codigoRespuesta);
                    }
                }
            }
            
            // Determinar estado del lote según código de respuesta
            if ("0300".equals(codigoRespuesta)) {
                lote.setEstado(EstadoLoteDE.EN_PROCESO);
            } else if ("0260".equals(codigoRespuesta)) {
                lote.setEstado(EstadoLoteDE.PROCESADO);
            } else {
                lote.setEstado(EstadoLoteDE.ERROR_RED);
            }
            
            lote.setFechaProcesado(LocalDateTime.now());
            lote.setFechaUltimoIntento(LocalDateTime.now());
            lote.setIntentos(1);
            lote.setRespuestaSifen(respuestaLote.getRespuestaBruta());
            
            // IMPORTANTE: Extraer y guardar el protocolo del lote
            String protocolo = extraerProtocoloDeRespuesta(respuestaLote.getRespuestaBruta());
            lote.setProtocolo(protocolo);
            
            logger.info("Guardando LoteDE con protocolo: {}", protocolo);
            LoteDE loteSaved = loteDEService.save(lote);
            logger.info("✅ LoteDE guardado exitosamente con ID: {}, Protocolo: {}", loteSaved.getId(), loteSaved.getProtocolo());
            
            // 2. Crear y guardar el DocumentoElectronico asociado al lote
            com.franco.dev.domain.financiero.DocumentoElectronico deDb = new com.franco.dev.domain.financiero.DocumentoElectronico();
            
            // Relacionar con la factura y el lote
            deDb.setFacturaLegal(factura);
            deDb.setLoteDe(loteSaved);
            
            // IMPORTANTE: Setear sucursal_id (requerido por constraint de BD)
            deDb.setSucursalId(factura.getSucursalId());
            
            // Datos del DE
            deDb.setCdc(deSifen.obtenerCDC());

            // obtener url qr
            deDb.setUrlQr(deSifen.getEnlaceQR());
            
            // Determinar estado del DE según código de respuesta
            if ("0260".equals(codigoRespuesta)) {
                deDb.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.APROBADO);
            } else if ("0300".equals(codigoRespuesta)) {
                deDb.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.EN_LOTE);
            } else {
                deDb.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.RECHAZADO);
            }
            
            // Guardar respuesta de SIFEN
            deDb.setCodigoRespuestaSifen(codigoRespuesta);
            
            // Extraer mensaje si es null
            String mensajeRespuesta = respuesta.getdMsgRes();
            if (mensajeRespuesta == null && respuesta.getRespuestaBruta() != null) {
                String respuestaBruta = respuesta.getRespuestaBruta();
                if (respuestaBruta.contains("dMsgRes")) {
                    int startMsg = respuestaBruta.indexOf("<ns2:dMsgRes>") + 13;
                    int endMsg = respuestaBruta.indexOf("</ns2:dMsgRes>");
                    if (startMsg > 13 && endMsg > startMsg) {
                        mensajeRespuesta = respuestaBruta.substring(startMsg, endMsg);
                        // Decodificar entidades HTML
                        mensajeRespuesta = mensajeRespuesta.replace("&#225;", "á")
                                                         .replace("&#233;", "é")
                                                         .replace("&#237;", "í")
                                                         .replace("&#243;", "ó")
                                                         .replace("&#250;", "ú")
                                                         .replace("&#241;", "ñ");
                        logger.info("Mensaje extraído de respuesta bruta: {}", mensajeRespuesta);
                    }
                }
            }
            deDb.setMensajeRespuestaSifen(mensajeRespuesta);
            
            logger.info("Guardando DocumentoElectronico asociado al lote...");
            com.franco.dev.domain.financiero.DocumentoElectronico deSaved = documentoElectronicoService.save(deDb);
            
            logger.info("✅ DocumentoElectronico guardado exitosamente:");
            logger.info("   ID: {}", deSaved.getId());
            logger.info("   CDC: {}", deSaved.getCdc());
            logger.info("   Estado: {}", deSaved.getEstado());
            logger.info("   Lote ID: {}", loteSaved.getId());
            logger.info("   Protocolo Lote: {}", loteSaved.getProtocolo());
            logger.info("   Código respuesta: {}", deSaved.getCodigoRespuestaSifen());
            logger.info("   Mensaje: {}", deSaved.getMensajeRespuestaSifen());
            
        } catch (Exception e) {
            logger.error("❌ Error al guardar LoteDE y DocumentoElectronico en BD: {}", e.getMessage(), e);
            throw new RuntimeException("Error al guardar en base de datos", e);
        }
    }
    
    /**
     * Extrae el número de protocolo del lote desde la respuesta XML de SIFEN.
     */
    private String extraerProtocoloDeRespuesta(String respuestaXml) {
        if (respuestaXml == null) {
            logger.warn("⚠️ RespuestaXml es null, no se puede extraer protocolo");
            return null;
        }
        
        try {
            // El protocolo está en el tag <ns2:dProtConsLote> dentro de <ns2:rResEnviLoteDe>
            // Ejemplo: <ns2:dProtConsLote>1078608211747495377</ns2:dProtConsLote>
            int startProtocolo = respuestaXml.indexOf("<ns2:dProtConsLote>") + 19;
            int endProtocolo = respuestaXml.indexOf("</ns2:dProtConsLote>");
            
            if (startProtocolo > 19 && endProtocolo > startProtocolo) {
                String protocolo = respuestaXml.substring(startProtocolo, endProtocolo);
                logger.info("✅ Protocolo extraído del XML: {}", protocolo);
                return protocolo;
            } else {
                logger.warn("⚠️ No se encontró tag <ns2:dProtConsLote> en la respuesta XML");
                logger.warn("   XML recibido: {}", respuestaXml);
                return null;
            }
        } catch (Exception e) {
            logger.error("❌ Error al extraer protocolo del XML: {}", e.getMessage());
            logger.error("   XML que causó el error: {}", respuestaXml);
            return null;
        }
    }
}
