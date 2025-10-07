package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.personas.ClienteService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Test del flujo completo SIFEN usando los métodos granulares de SifenService.
 * Este test valida la integración completa: Crear DE → Crear Lote → Enviar → Consultar.
 * 
 * Basado en SifenFlujoCompletoDELote pero usando los métodos del servicio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
public class SifenFlujoServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(SifenFlujoServiceTest.class);
    
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
    
    @Autowired
    private SifenService sifenService;

    /**
     * PASO 1: Crear facturas y sus respectivos Documentos Electrónicos usando SifenService.
     * Usa: sifenService.crearDocumentoElectronico()
     */
    @Test
    @Transactional
    // @Commit
    public void paso1_crearFacturasYDocumentosElectronicos() throws Exception {
        logger.info("=== PASO 1: CREANDO FACTURAS Y DEs USANDO SIFEN SERVICE ===");
        
        // Crear 3 facturas de prueba: 2 para cliente 194 (RUC válido) y 1 para cliente 2
        Long[] clienteIds = {958L, null, 959L, 25L};
        String[] descripciones = {
            "Cliente 958 municipalidad de salto", 
            "Cliente null - sin nombre", 
            "Cliente 959 - Franco emprendimientos eas", 
            "Cliente 25 - no tributa"
        };
        
        int exitosos = 0;
        int errores = 0;
        
        for (int i = 0; i < clienteIds.length; i++) {
            logger.info("--- Creando factura {} - {} ---", i + 1, descripciones[i]);
            
            try {
                // 1. Crear factura
                FacturaLegal factura = crearFacturaParaCliente(clienteIds[i], i + 1);
                logger.info("✅ Factura creada - ID: {}, Cliente: {}", factura.getId(), clienteIds[i]);
                
                // 2. Crear Documento Electrónico usando el servicio
                com.franco.dev.domain.financiero.DocumentoElectronico de = 
                    sifenService.crearDocumentoElectronico(factura);
                
                logger.info("✅ DE creado exitosamente:");
                logger.info("   - ID: {}", de.getId());
                logger.info("   - CDC: {}", de.getCdc());
                logger.info("   - Estado: {}", de.getEstado());
                logger.info("   - URL QR: {}", de.getUrlQr() != null ? "Generada" : "null");
                logger.info("   - XML Original: {} caracteres", 
                    de.getXmlOriginal() != null ? de.getXmlOriginal().length() : 0);
                
                exitosos++;
                
            } catch (Exception e) {
                logger.error("❌ Error al crear factura/DE {}: {}", i + 1, e.getMessage());
                errores++;
                
                if (clienteIds[i] == 2L) {
                    logger.warn("⚠️ Error esperado para cliente 2 (tributa=false o RUC inválido)");
                } else {
                    logger.error("❌ Error INESPERADO para cliente válido");
                    logger.error("Detalles:", e);
                }
            }
        }
        
        logger.info("=== RESUMEN PASO 1 ===");
        logger.info("   ✅ Exitosos: {}", exitosos);
        logger.info("   ❌ Errores: {}", errores);
        logger.info("=== FIN DE PASO 1 ===");
    }

    /**
     * PASO 2: Crear lotes y enviarlos a SIFEN usando SifenService.
     * Usa: sifenService.crearLote(), vincularDocumentosALote(), enviarLote()
     */
    @Test
    @Transactional
    @Commit
    public void paso2_crearYEnviarLotes() throws Exception {
        logger.info("=== PASO 2: CREANDO Y ENVIANDO LOTES USANDO SIFEN SERVICE ===");
        
        // 1. Buscar DEs pendientes
        List<com.franco.dev.domain.financiero.DocumentoElectronico> desPendientes = 
            documentoElectronicoService.findByEstado(EstadoDE.PENDIENTE);
        
        if (desPendientes.isEmpty()) {
            logger.warn("ℹ️ No hay DEs pendientes para crear lotes");
            logger.info("💡 Ejecuta primero: paso1_crearFacturasYDocumentosElectronicos()");
            return;
        }
        
        logger.info("📋 Encontrados {} DEs pendientes", desPendientes.size());
        
        // 2. Dividir en lotes de máximo 50
        int maxSizePorLote = 50;
        List<List<com.franco.dev.domain.financiero.DocumentoElectronico>> lotes = 
            dividirEnLotes(desPendientes, maxSizePorLote);
        
        logger.info("📦 Se crearán {} lotes", lotes.size());
        
        int lotesEnviados = 0;
        int lotesConError = 0;
        
        for (int i = 0; i < lotes.size(); i++) {
            List<com.franco.dev.domain.financiero.DocumentoElectronico> documentosLote = lotes.get(i);
            logger.info("--- Procesando lote {} con {} DEs ---", i + 1, documentosLote.size());
            
            try {
                // 2A. Crear lote vacío
                LoteDE lote = sifenService.crearLote();
                logger.info("   ✅ Lote creado - ID: {}", lote.getId());
                
                // 2B. Vincular documentos al lote
                sifenService.vincularDocumentosALote(lote, documentosLote);
                logger.info("   ✅ {} documentos vinculados al lote", documentosLote.size());
                
                // 2C. Enviar lote a SIFEN
                sifenService.enviarLote(lote);
                
                // Recargar lote para ver estado actualizado
                lote = loteDEService.findById(lote.getId()).orElse(lote);
                logger.info("   📥 Respuesta de SIFEN:");
                logger.info("      - Estado del lote: {}", lote.getEstado());
                logger.info("      - Protocolo: {}", lote.getProtocolo());
                
                if (lote.getEstado() == EstadoLoteDE.EN_PROCESO) {
                    lotesEnviados++;
                    logger.info("   ✅ Lote {} enviado exitosamente", lote.getId());
                } else {
                    lotesConError++;
                    logger.error("   ❌ Lote {} con error: {}", lote.getId(), lote.getEstado());
                }
                
            } catch (Exception e) {
                logger.error("❌ Error al procesar lote {}: {}", i + 1, e.getMessage());
                logger.error("Detalles:", e);
                lotesConError++;
            }
        }
        
        logger.info("=== RESUMEN PASO 2 ===");
        logger.info("   📦 Lotes enviados exitosamente: {}", lotesEnviados);
        logger.info("   ❌ Lotes con error: {}", lotesConError);
        logger.info("=== FIN DE PASO 2 ===");
    }

    /**
     * PASO 3: Consultar lotes EN_PROCESO usando SifenService.
     * Usa: sifenService.consultarLote()
     */
    @Test
    @Transactional
    @Commit
    public void paso3_consultarLotesEnProceso() throws Exception {
        logger.info("=== PASO 3: CONSULTANDO LOTES EN PROCESO USANDO SIFEN SERVICE ===");
        
        // 1. Buscar lotes en proceso
        List<LoteDE> lotesEnProceso = loteDEService.findByEstado(EstadoLoteDE.EN_PROCESO);
        
        if (lotesEnProceso.isEmpty()) {
            logger.warn("ℹ️ No hay lotes en proceso para consultar");
            logger.info("💡 Ejecuta primero: paso2_crearYEnviarLotes()");
            return;
        }
        
        logger.info("📋 Encontrados {} lotes en proceso", lotesEnProceso.size());
        
        int lotesConsultados = 0;
        int lotesProcesados = 0;
        int lotesAunEnProceso = 0;
        int lotesConError = 0;
        
        for (LoteDE lote : lotesEnProceso) {
            logger.info("--- Consultando lote ID: {} con protocolo: {} ---", 
                lote.getId(), lote.getProtocolo());
            
            try {
                // Consultar estado del lote en SIFEN
                sifenService.consultarLote(lote);
                
                // Recargar lote para ver estado actualizado
                lote = loteDEService.findById(lote.getId()).orElse(lote);
                
                logger.info("   📥 Resultado de consulta:");
                logger.info("      - Estado del lote: {}", lote.getEstado());
                logger.info("      - Fecha procesado: {}", lote.getFechaProcesado());
                
                lotesConsultados++;
                
                // Contar según estado final
                switch (lote.getEstado()) {
                    case PROCESADO:
                        lotesProcesados++;
                        logger.info("   ✅ Lote {} PROCESADO exitosamente", lote.getId());
                        
                        // Mostrar resumen de documentos
                        List<com.franco.dev.domain.financiero.DocumentoElectronico> docs = 
                            documentoElectronicoService.findByLoteDe(lote);
                        long aprobados = docs.stream().filter(d -> d.getEstado() == EstadoDE.APROBADO).count();
                        long rechazados = docs.stream().filter(d -> d.getEstado() == EstadoDE.RECHAZADO).count();
                        logger.info("      📊 Documentos: {} aprobados, {} rechazados", aprobados, rechazados);
                        break;
                        
                    case PROCESADO_CON_ERRORES:
                        lotesProcesados++;
                        logger.warn("   ⚠️ Lote {} PROCESADO CON ERRORES", lote.getId());
                        break;
                        
                    case EN_PROCESO:
                        lotesAunEnProceso++;
                        logger.info("   ⏳ Lote {} aún EN PROCESO", lote.getId());
                        break;
                        
                    case RECHAZADO:
                    case ERROR_PERMANENTE:
                        lotesConError++;
                        logger.error("   ❌ Lote {} con error permanente: {}", lote.getId(), lote.getEstado());
                        break;
                        
                    default:
                        logger.warn("   ⚠️ Lote {} con estado inesperado: {}", lote.getId(), lote.getEstado());
                        break;
                }
                
            } catch (Exception e) {
                logger.error("❌ Error al consultar lote {}: {}", lote.getId(), e.getMessage());
                logger.error("Detalles:", e);
                lotesConError++;
            }
        }
        
        logger.info("=== RESUMEN PASO 3 ===");
        logger.info("   📋 Lotes consultados: {}", lotesConsultados);
        logger.info("   ✅ Lotes procesados exitosamente: {}", lotesProcesados);
        logger.info("   ⏳ Lotes aún en proceso: {}", lotesAunEnProceso);
        logger.info("   ❌ Lotes con error: {}", lotesConError);
        logger.info("=== FIN DE PASO 3 ===");
    }

    /**
     * TEST COMPLETO: Ejecuta los 3 pasos secuencialmente.
     * ADVERTENCIA: Este test puede tardar varios minutos en completarse.
     */
    @Test
    @Transactional
    @Commit
    public void testFlujoCompletoSIFEN() throws Exception {
        logger.info("╔══════════════════════════════════════════════════════════════╗");
        logger.info("║  TEST COMPLETO DEL FLUJO SIFEN USANDO SIFEN SERVICE        ║");
        logger.info("╚══════════════════════════════════════════════════════════════╝");
        
        // PASO 1: Crear facturas y DEs
        paso1_crearFacturasYDocumentosElectronicos();
        
        logger.info("\n⏸️  Pausa de 2 segundos entre pasos...\n");
        Thread.sleep(2000);
        
        // PASO 2: Crear y enviar lotes
        paso2_crearYEnviarLotes();
        
        logger.info("\n⏸️  Pausa de 5 segundos para permitir procesamiento en SIFEN...\n");
        Thread.sleep(5000);
        
        // PASO 3: Consultar lotes
        paso3_consultarLotesEnProceso();
        
        logger.info("╔══════════════════════════════════════════════════════════════╗");
        logger.info("║  TEST COMPLETO FINALIZADO                                   ║");
        logger.info("╚══════════════════════════════════════════════════════════════╝");
    }

    // ===================== MÉTODOS HELPER =====================

    /**
     * Crea una factura de prueba para un cliente específico.
     * Si clienteId es null, se crea una factura sin cliente (innominado).
     */
    private FacturaLegal crearFacturaParaCliente(Long clienteId, int numeroFactura) {
        // Crear factura
        FacturaLegal factura = new FacturaLegal();
        factura.setSucursalId(24L);
        factura.setFecha(LocalDateTime.now());
        factura.setCredito(false); // Contado
        
        // Cargar cliente (puede ser null para facturas innominadas)
        if (clienteId != null) {
            Cliente cliente = clienteService.findById(clienteId).orElse(null);
            if (cliente == null) {
                throw new IllegalArgumentException("Cliente con ID " + clienteId + " no encontrado");
            }
            factura.setCliente(cliente);
        } else {
            // Factura sin cliente (innominado)
            factura.setCliente(null);
        }
        
        // Cargar timbrado detalle
        TimbradoDetalle timbradoDetalle = timbradoDetalleService.findById(93L).orElse(null);
        if (timbradoDetalle == null) {
            throw new IllegalArgumentException("TimbradoDetalle con ID 93 no encontrado");
        }
        factura.setTimbradoDetalle(timbradoDetalle);
        
        // Obtener siguiente número correlativo
        Long siguienteNumero = timbradoDetalle.getNumeroActual() != null ? 
            timbradoDetalle.getNumeroActual() + 1 : 1L;
        factura.setNumeroFactura(siguienteNumero.intValue());
        
        // Guardar factura
        factura = facturaLegalService.save(factura);
        
        // Crear items
        List<FacturaLegalItem> items = crearItemsParaFactura(factura, numeroFactura);
        
        // Calcular totales
        double totalItems = items.stream()
            .mapToDouble(item -> item.getCantidad().floatValue() * item.getPrecioUnitario().doubleValue())
            .sum();
        
        double totalIva = totalItems * 0.10; // 10% IVA
        double totalFinal = totalItems + totalIva;
        
        factura.setTotalFinal(totalFinal);
        factura = facturaLegalService.save(factura);
        
        // Actualizar número actual en TimbradoDetalle
        timbradoDetalle.setNumeroActual(siguienteNumero);
        timbradoDetalleService.save(timbradoDetalle);
        
        return factura;
    }

    /**
     * Crea items aleatorios para una factura.
     */
    private List<FacturaLegalItem> crearItemsParaFactura(FacturaLegal factura, int numeroFactura) {
        List<FacturaLegalItem> items = new ArrayList<>();
        
        String[] productos = {
            "TRES LEONES ETIQUETA NEGRA 750ML",
            "COCA COLA 500ML", 
            "PAN INTEGRAL 500G",
            "AGUA MINERAL 600ML",
            "CHOCOLATE NEGRO 100G"
        };
        
        double[] precios = {15000.0, 8000.0, 12000.0, 5000.0, 7000.0};
        
        // Crear 2-3 items
        int numItems = 2 + (numeroFactura % 2);
        
        for (int i = 0; i < numItems; i++) {
            FacturaLegalItem item = new FacturaLegalItem();
            item.setFacturaLegal(factura);
            item.setSucursalId(factura.getSucursalId());
            item.setDescripcion(productos[i % productos.length]);
            item.setCantidad(1.0f + i);
            item.setPrecioUnitario(precios[i % precios.length]);
            item.setTotal(item.getCantidad() * item.getPrecioUnitario());
            
            item = facturaLegalItemService.save(item);
            items.add(item);
        }
        
        return items;
    }

    /**
     * Divide una lista de documentos en lotes más pequeños.
     */
    private List<List<com.franco.dev.domain.financiero.DocumentoElectronico>> dividirEnLotes(
            List<com.franco.dev.domain.financiero.DocumentoElectronico> des, int maxSize) {
        
        List<List<com.franco.dev.domain.financiero.DocumentoElectronico>> lotes = new ArrayList<>();
        
        for (int i = 0; i < des.size(); i += maxSize) {
            int end = Math.min(i + maxSize, des.size());
            lotes.add(des.subList(i, end));
        }
        
        return lotes;
    }
}

