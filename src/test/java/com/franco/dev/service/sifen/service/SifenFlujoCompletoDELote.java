package com.franco.dev.service.sifen.service;

import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionLoteDE;
import com.roshka.sifen.core.beans.response.RespuestaConsultaLoteDE;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.core.fields.request.de.*;
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
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
// import com.franco.dev.service.sifen.util.CodigosGeograficos;
import com.franco.dev.utilitarios.CalcularVerificadorRuc;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Test especializado para el flujo completo de documentos electrónicos por lotes.
 * Cada método es ejecutable por separado y maneja una responsabilidad específica.
 * 
 * Basado en los métodos funcionales de SifenDETest.java
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
public class SifenFlujoCompletoDELote {

    private static final Logger logger = LoggerFactory.getLogger(SifenFlujoCompletoDELote.class);
    
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
    
    @Autowired
    private com.roshka.sifen.core.SifenConfig sifenConfig;
    
    // Campo temporal para almacenar detalles de documentos procesados
    private List<DetalleDocumentoEnLote> detallesDocumentosTemporales;

    /**
     * PASO 1: Crear facturas y sus respectivos Documentos Electrónicos sin enviarlos a SIFEN.
     * 
     * Este método crea facturas de prueba y genera los DE correspondientes
     * con estado PENDIENTE (listos para ser incluidos en un lote).
     */
    @Test
    @Transactional
    @Commit
    public void crearFacturasAndDE() throws Exception {
        logger.info("=== PASO 1: CREANDO FACTURAS Y DOCUMENTOS ELECTRÓNICOS ===");
        
        // Crear 3 facturas de prueba: 2 para cliente 194 (RUC válido) y 1 para cliente 2 (RUC inválido)
        List<FacturaLegal> facturas = new ArrayList<>();
        
        // Configuración de clientes para el test
        Long[] clienteIds = {194L, 194L, 2L}; // 2 facturas para cliente 194, 1 para cliente 2
        String[] descripciones = {"Cliente 194 (RUC válido)", "Cliente 194 (RUC válido)", "Cliente 2 (RUC inválido)"};
        
        for (int i = 1; i <= 3; i++) {
            logger.info("--- Creando factura {} - {} ---", i, descripciones[i-1]);
            
            // Crear nueva factura
            FacturaLegal factura = new FacturaLegal();
            factura.setSucursalId(24L);
            factura.setFecha(LocalDateTime.now());
            factura.setCredito(false); // Contado
            
            // Cargar cliente según configuración
            Long clienteId = clienteIds[i-1];
            Cliente cliente = clienteService.findById(clienteId).orElse(null);
            if (cliente == null) {
                throw new IllegalArgumentException("Cliente con ID " + clienteId + " no encontrado");
            }
            factura.setCliente(cliente);
            
            logger.info("📋 Cliente asignado: ID={}, Nombre={}, RUC={}, Tributa={}", 
                cliente.getId(), 
                cliente.getPersona() != null ? cliente.getPersona().getNombre() : "N/A",
                cliente.getPersona() != null ? cliente.getPersona().getDocumento() : "N/A",
                cliente.getTributa());
            
            TimbradoDetalle timbradoDetalle = timbradoDetalleService.findById(93L).orElse(null);
            if (timbradoDetalle == null) {
                throw new IllegalArgumentException("TimbradoDetalle con ID 93 no encontrado");
            }
            factura.setTimbradoDetalle(timbradoDetalle);
            
            // Obtener siguiente número correlativo del timbrado detalle
            Long siguienteNumero = timbradoDetalle.getNumeroActual() != null ? 
                timbradoDetalle.getNumeroActual() + 1 : 1L;
            factura.setNumeroFactura(siguienteNumero.intValue());
            
            logger.info("✅ Número de factura asignado: {} (anterior: {})", 
                siguienteNumero, timbradoDetalle.getNumeroActual());
            
            // Guardar factura
            factura = facturaLegalService.save(factura);
            logger.info("✅ Factura creada con ID: {}", factura.getId());
            
            // Crear items aleatorios
            List<FacturaLegalItem> items = crearItemsParaFactura(factura, i);
            
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
            
            facturas.add(factura);
            
            // Crear Documento Electrónico para esta factura (SIN ENVIAR A SIFEN)
            try {
                crearDocumentoElectronicoParaFactura(factura);
                logger.info("✅ DE creado exitosamente para factura {} (Cliente {})", factura.getId(), clienteId);
            } catch (Exception e) {
                logger.error("❌ Error al crear DE para factura {} (Cliente {}): {}", 
                    factura.getId(), clienteId, e.getMessage());
                
                // Log detallado del error para análisis
                if (clienteId == 2L) {
                    logger.warn("⚠️ Error esperado para cliente 2 (RUC no habilitado para facturación electrónica)");
                    logger.info("🔍 Detalles del cliente 2: {}", cliente);
                    if (cliente.getPersona() != null) {
                        logger.info("🔍 Documento del cliente: {}", cliente.getPersona().getDocumento());
                        logger.info("🔍 Tributa: {}", cliente.getTributa());
                    }
                } else {
                    logger.error("❌ Error inesperado para cliente válido {}", clienteId);
                }
            }
        }
        
        logger.info("✅ PASO 1 COMPLETADO: {} facturas creadas con sus DEs en estado PENDIENTE", facturas.size());
        logger.info("=== FIN DE CREACIÓN DE FACTURAS Y DEs ===");
    }

    /**
     * PASO 1B: Crear facturas INNOMINADAS para test de nominación.
     * 
     * Este método crea facturas de prueba con cliente=null (innominadas)
     * que luego pueden ser nominados con el servicio de eventos SIFEN.
     * 
     * INNOMINADO significa:
     * - Receptor: "Sin Nombre"
     * - Documento: "0"
     * - Tipo: INNOMINADO
     * - Solo permitido para montos < 7.000.000 PYG
     */
    @Test
    @Transactional
    @Commit
    public void crearFacturasInnominadas() throws Exception {
        logger.info("=== PASO 1B: CREANDO FACTURAS INNOMINADAS (CLIENTE = NULL) ===");
        
        List<FacturaLegal> facturas = new ArrayList<>();
        int cantidadFacturas = 2;
        
        for (int i = 1; i <= cantidadFacturas; i++) {
            logger.info("\n--- Creando factura innominada {} ---", i);
            
            // 1. Crear factura SIN CLIENTE (cliente = null)
            FacturaLegal factura = new FacturaLegal();
            factura.setSucursalId(24L);
            factura.setFecha(LocalDateTime.now());
            factura.setCredito(false); // Contado
            factura.setCliente(null); // ✅ CLAVE: Cliente NULL para innominado
            
            // 2. Cargar timbrado detalle
            TimbradoDetalle timbradoDetalle = timbradoDetalleService.findById(93L).orElse(null);
            if (timbradoDetalle == null) {
                throw new IllegalArgumentException("TimbradoDetalle con ID 93 no encontrado");
            }
            factura.setTimbradoDetalle(timbradoDetalle);
            
            // 3. Obtener siguiente número correlativo
            Long siguienteNumero = timbradoDetalle.getNumeroActual() != null ? 
                timbradoDetalle.getNumeroActual() + 1 : 1L;
            factura.setNumeroFactura(siguienteNumero.intValue());
            
            logger.info("✅ Número de factura asignado: {}", siguienteNumero);
            
            // 4. Guardar factura
            factura = facturaLegalService.save(factura);
            logger.info("✅ Factura creada con ID: {} (Cliente: NULL - Innominado)", factura.getId());
            
            // 5. Crear items aleatorios
            List<FacturaLegalItem> items = crearItemsParaFactura(factura, i);
            
            // 6. Calcular totales (IMPORTANTE: Debe ser < 7.000.000 para innominado)
            double totalItems = items.stream()
                .mapToDouble(item -> item.getCantidad().floatValue() * item.getPrecioUnitario().doubleValue())
                .sum();
            
            double totalIva = totalItems * 0.10; // 10% IVA
            double totalFinal = totalItems + totalIva;
            
            // Validar que no exceda el monto máximo para innominado
            if (totalFinal >= 7_000_000.0) {
                logger.warn("⚠️  Total {} excede el máximo para innominado (7.000.000). Ajustando...", totalFinal);
                totalFinal = 6_500_000.0; // Ajustar a un valor seguro
            }
            
            factura.setTotalFinal(totalFinal);
            factura = facturaLegalService.save(factura);
            
            logger.info("💰 Total factura: {} (apto para innominado)", totalFinal);
            
            // 7. Actualizar número actual en TimbradoDetalle
            timbradoDetalle.setNumeroActual(siguienteNumero);
            timbradoDetalleService.save(timbradoDetalle);
            
            facturas.add(factura);
            
            // 8. Crear Documento Electrónico INNOMINADO usando SifenService
            logger.info("📝 Creando DE innominado usando SifenService...");
            try {
                // ✅ USAR SifenService que ya maneja cliente=null correctamente
                com.franco.dev.domain.financiero.DocumentoElectronico de = 
                    sifenService.crearDocumentoElectronico(factura);
                
                logger.info("✅ DE innominado creado exitosamente");
                logger.info("   - ID: {}", de.getId());
                logger.info("   - CDC: {}", de.getCdc());
                logger.info("   - Estado: {}", de.getEstado());
                logger.info("   - Receptor: Sin Nombre (INNOMINADO)");
                logger.info("   - Documento: 0");
                logger.info("📋 Este DE puede ser nominado posteriormente usando SifenEventoService.nominarReceptor()");
                
            } catch (Exception e) {
                logger.error("❌ Error al crear DE innominado para factura {}: {}", 
                    factura.getId(), e.getMessage(), e);
                throw e; // Re-lanzar para que el test falle si hay error
            }
        }
        
        logger.info("\n✅ PASO 1B COMPLETADO: {} facturas innominadas creadas con sus DEs", facturas.size());
        logger.info("💡 SIGUIENTE PASO: Ejecuta crearLotes() y luego enviarLote() para enviar a SIFEN");
        logger.info("💡 PARA NOMINACIÓN: Usa el test testNominacionReceptor en SifenEventoServiceTest");
        logger.info("=== FIN DE CREACIÓN DE FACTURAS INNOMINADAS ===");
    }

    /**
     * TEST DE VALIDACIÓN EXHAUSTIVA: Crear 4 facturas con diferentes escenarios de validación.
     * 
     * Escenarios a probar:
     * 1. Factura válida (cliente con RUC válido) - Debería funcionar ✅
     * 2. Factura para cliente sin nombre - Validar manejo de error
     * 3. Factura para cliente empresa (ID 798) - Validar datos de empresa
     * 4. Factura para cliente con número de cédula equivocado (ID 25) - Debería dar error ❌
     */
    @Test
    @Transactional
    @Commit
    public void testValidacionEscenariosError() throws Exception {
        logger.info("=== TEST DE VALIDACIÓN EXHAUSTIVA: 4 ESCENARIOS ===");
        
        // Configuración de escenarios
        Long[] clienteIds = {
            194L,  // Escenario 1: Cliente válido con RUC
            null,  // Escenario 2: Cliente sin nombre (buscaremos uno)
            798L,  // Escenario 3: Cliente empresa
            25L    // Escenario 4: Cliente con cédula equivocada
        };
        
        String[] descripciones = {
            "Cliente válido con RUC",
            "Cliente sin nombre",
            "Cliente empresa",
            "Cliente con cédula equivocada"
        };
        
        List<FacturaLegal> facturas = new ArrayList<>();
        int exitosos = 0;
        int erroresEsperados = 0;
        int erroresInesperados = 0;
        
        for (int i = 0; i < clienteIds.length; i++) {
            int escenario = i + 1;
            logger.info("\n📌 ESCENARIO {}: {}", escenario, descripciones[i]);
            logger.info("============================================================");
            
            try {
                // Crear nueva factura
                FacturaLegal factura = new FacturaLegal();
                factura.setSucursalId(24L);
                factura.setFecha(LocalDateTime.now());
                factura.setCredito(false); // Contado
                
                // Cargar cliente según escenario
                Long clienteId = clienteIds[i];
                Cliente cliente = null;
                
                // Escenario 2: Buscar cliente sin nombre
                if (i == 1) {
                    logger.info("🔍 Buscando cliente sin nombre en la base de datos...");
                    // Intentar encontrar un cliente sin nombre (esto puede variar según la BD)
                    cliente = clienteService.findById(2L).orElse(null); // Cliente 2 como ejemplo
                    if (cliente != null) {
                        logger.info("✅ Cliente encontrado: ID={}, Nombre={}", 
                            cliente.getId(), 
                            cliente.getPersona() != null ? cliente.getPersona().getNombre() : "NULL");
                    }
                } else {
                    cliente = clienteService.findById(clienteId).orElse(null);
                }
                
                if (cliente == null) {
                    logger.error("❌ Cliente con ID {} no encontrado", clienteId);
                    erroresEsperados++;
                    continue;
                }
                
                factura.setCliente(cliente);
                
                // Logs detallados del cliente
                logger.info("📋 Información del Cliente:");
                logger.info("   ID: {}", cliente.getId());
                logger.info("   Nombre: {}", cliente.getPersona() != null ? cliente.getPersona().getNombre() : "NULL");
                logger.info("   Documento: {}", cliente.getPersona() != null ? cliente.getPersona().getDocumento() : "NULL");
                logger.info("   Tributa: {}", cliente.getTributa());
                
                TimbradoDetalle timbradoDetalle = timbradoDetalleService.findById(93L).orElse(null);
                if (timbradoDetalle == null) {
                    throw new IllegalArgumentException("TimbradoDetalle con ID 93 no encontrado");
                }
                factura.setTimbradoDetalle(timbradoDetalle);
                
                // Obtener siguiente número correlativo del timbrado detalle
                Long siguienteNumero = timbradoDetalle.getNumeroActual() != null ? 
                    timbradoDetalle.getNumeroActual() + 1 : 1L;
                factura.setNumeroFactura(siguienteNumero.intValue());
                
                logger.info("✅ Número de factura asignado: {}", siguienteNumero);
                
                // Guardar factura
                factura = facturaLegalService.save(factura);
                logger.info("✅ Factura creada con ID: {}", factura.getId());
                
                // Crear items aleatorios
                List<FacturaLegalItem> items = crearItemsParaFactura(factura, escenario);
                
                // Calcular totales
                double totalItems = items.stream()
                    .mapToDouble(item -> item.getCantidad().floatValue() * item.getPrecioUnitario().doubleValue())
                    .sum();
                
                logger.info("💰 Total items: {}", totalItems);
                
                // Intentar crear el Documento Electrónico
                logger.info("🔧 Intentando crear Documento Electrónico...");
                
                try {
                    crearDocumentoElectronicoParaFactura(factura);
                    logger.info("✅ ESCENARIO {} EXITOSO: DE creado correctamente", escenario);
                    facturas.add(factura);
                    exitosos++;
                } catch (Exception deError) {
                    logger.error("❌ Error al crear DE para escenario {}: {}", escenario, deError.getMessage());
                    
                    // Determinar si el error era esperado
                    if (i == 1 || i == 3) { // Escenarios 2 y 4: errores esperados
                        logger.info("ℹ️  Error ESPERADO para escenario {} ({})", escenario, descripciones[i]);
                        erroresEsperados++;
                    } else {
                        logger.error("⚠️  Error INESPERADO para escenario {} ({})", escenario, descripciones[i]);
                        erroresInesperados++;
                    }
                    
                    // Log del stack trace para debugging
                    logger.debug("Stack trace:", deError);
                }
                
            } catch (Exception e) {
                logger.error("❌ Error general en escenario {}: {}", escenario, e.getMessage());
                erroresInesperados++;
                logger.debug("Stack trace:", e);
            }
        }
        
        logger.info("\n============================================================");
        logger.info("📊 RESUMEN DE VALIDACIÓN:");
        logger.info("   ✅ Exitosos: {}", exitosos);
        logger.info("   ⚠️  Errores esperados: {}", erroresEsperados);
        logger.info("   ❌ Errores inesperados: {}", erroresInesperados);
        logger.info("   📋 Total facturas creadas: {}", facturas.size());
        logger.info("=== FIN DE TEST DE VALIDACIÓN ===");
    }

    /**
     * PASO 2: Crear lotes con todos los Documentos Electrónicos pendientes y enviarlos a SIFEN.
     * 
     * Este método busca todos los DEs con estado PENDIENTE, los agrupa en lotes
     * y los envía a SIFEN, actualizando sus estados a EN_LOTE.
     */
    @Test
    @Transactional
    @Commit
    public void crearLotes() throws Exception {
        logger.info("=== PASO 2: CREANDO Y ENVIANDO LOTES DE DEs PENDIENTES ===");
        
        // Buscar todos los DEs pendientes (sin lote asignado)
        List<com.franco.dev.domain.financiero.DocumentoElectronico> desPendientes = 
            documentoElectronicoService.findByEstado(com.franco.dev.domain.financiero.enums.EstadoDE.PENDIENTE);
        
        if (desPendientes.isEmpty()) {
            logger.info("ℹ️ No hay DEs pendientes para crear lotes");
            return;
        }
        
        logger.info("📋 Encontrados {} DEs pendientes para procesar", desPendientes.size());
        
        // Agrupar DEs en lotes (máximo 50 por lote según configuración SIFEN)
        int maxSizePorLote = 50;
        List<List<com.franco.dev.domain.financiero.DocumentoElectronico>> lotes = 
            dividirEnLotes(desPendientes, maxSizePorLote);
        
        logger.info("📦 Se crearán {} lotes", lotes.size());
        
        for (int i = 0; i < lotes.size(); i++) {
            List<com.franco.dev.domain.financiero.DocumentoElectronico> loteDEs = lotes.get(i);
            logger.info("--- Procesando lote {} con {} DEs ---", i + 1, loteDEs.size());
            
            // Crear lote en BD
            LoteDE lote = new LoteDE();
            lote.setEstado(EstadoLoteDE.PENDIENTE_ENVIO);
            lote.setFechaUltimoIntento(LocalDateTime.now());
            lote.setIntentos(0);
            lote = loteDEService.save(lote);
            
            logger.info("✅ Lote creado con ID: {}", lote.getId());
            
            // Actualizar DEs para asignarlos al lote
            for (com.franco.dev.domain.financiero.DocumentoElectronico de : loteDEs) {
                de.setLoteDe(lote);
                de.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.EN_LOTE);
                documentoElectronicoService.save(de);
            }
            
            // Reconstruir los DE de SIFEN para el lote usando el XML original guardado.
            List<DocumentoElectronico> deSifenLote = new ArrayList<>();
            logger.info("🔄 Reconstruyendo objetos DE de SIFEN para el lote...");
            for (com.franco.dev.domain.financiero.DocumentoElectronico de : loteDEs) {
                logger.info("   - Procesando DE ID: {}, Factura ID: {}", de.getId(), de.getFacturaLegal().getId());
                logger.info("     CDC Original (DB): {}", de.getCdc());

                DocumentoElectronico deSifen;
                
                // ESTRATEGIA ÓPTIMA: Reconstruir desde XML guardado si está disponible
                if (de.getXmlOriginal() != null && !de.getXmlOriginal().isEmpty()) {
                    logger.info("     ✅ XML original encontrado - Reconstruyendo DE desde XML...");
                    try {
                        // Reconstruir el DocumentoElectronico EXACTO desde el XML original
                        // El CDC se extrae automáticamente del XML, no necesita pasarse por separado
                        deSifen = new DocumentoElectronico(de.getXmlOriginal());
                        logger.info("     ✅ DE reconstruido exitosamente desde XML");
                        logger.info("     CDC del DE reconstruido: {}", deSifen.obtenerCDC());
                        
                        // Verificar que el CDC coincide
                        if (!de.getCdc().equals(deSifen.obtenerCDC())) {
                            logger.error("     ❌ ERROR CRÍTICO: El CDC del DE reconstruido NO coincide!");
                            logger.error("        Esperado: {}", de.getCdc());
                            logger.error("        Obtenido: {}", deSifen.obtenerCDC());
                        }
                    } catch (Exception e) {
                        logger.error("     ❌ Error al reconstruir DE desde XML: {}", e.getMessage());
                        logger.warn("     ⚠️  Fallback: Usando método de regenerar y forzar CDC...");
                        deSifen = reconstruirDEConFallback(de);
                    }
                } else {
                    // FALLBACK: Si no hay XML guardado, usar el método anterior de regenerar y forzar
                    logger.warn("     ⚠️  XML original no disponible - Usando método de fallback (regenerar y forzar CDC)");
                    deSifen = reconstruirDEConFallback(de);
                }
                
                // Asegurar que tiene el QR correcto
                deSifen.setEnlaceQR(de.getUrlQr());
                
                deSifenLote.add(deSifen);
                logger.info("     ✅ DE añadido al lote - CDC: {}", deSifen.obtenerCDC());
            }
            
            // Enviar lote a SIFEN
            logger.info("📤 Enviando lote {} a SIFEN con {} DEs...", lote.getId(), deSifenLote.size());
            RespuestaRecepcionLoteDE respuesta = Sifen.recepcionLoteDE(deSifenLote);
            
            logger.info("📥 Respuesta recibida - Código: {}, Mensaje: {}", 
                respuesta.getdCodRes(), respuesta.getdMsgRes());
            
            // Actualizar lote con respuesta
            lote.setFechaUltimoIntento(LocalDateTime.now());
            lote.setRespuestaSifen(respuesta.getRespuestaBruta());
            lote.setIntentos(1);
            
            // Determinar estado del lote según respuesta
            if ("0300".equals(respuesta.getdCodRes())) {
                lote.setEstado(EstadoLoteDE.EN_PROCESO);
                String protocolo = extraerProtocoloDeRespuesta(respuesta.getRespuestaBruta());
                lote.setProtocolo(protocolo);
                logger.info("✅ Lote {} enviado exitosamente. Protocolo: {}", lote.getId(), protocolo);
            } else {
                lote.setEstado(EstadoLoteDE.ERROR_ENVIO);
                logger.error("❌ Error al enviar lote {}: {}", lote.getId(), respuesta.getdMsgRes());
            }
            
            loteDEService.save(lote);
        }
        
        logger.info("✅ PASO 2 COMPLETADO: {} lotes procesados", lotes.size());
        logger.info("=== FIN DE CREACIÓN Y ENVÍO DE LOTES ===");
    }

    /**
     * PASO 3: Consultar todos los lotes con estado EN_PROCESO.
     * 
     * Este método busca todos los lotes que están siendo procesados por SIFEN
     * y consulta su estado, actualizando los resultados.
     */
    @Test
    @Transactional
    @Commit
    public void consultarLotesPendientes() throws Exception {
        logger.info("=== PASO 3: CONSULTANDO LOTES EN PROCESO ===");
        
        // Buscar lotes en estado EN_PROCESO (optimizado - solo trae los que necesitamos)
        List<LoteDE> lotesEnProceso = loteDEService.findByEstado(EstadoLoteDE.EN_PROCESO);
        
        if (lotesEnProceso.isEmpty()) {
            logger.info("ℹ️ No hay lotes en proceso para consultar");
            return;
        }
        
        logger.info("📋 Encontrados {} lotes en proceso", lotesEnProceso.size());
        
        for (LoteDE lote : lotesEnProceso) {
            logger.info("--- Consultando lote {} con protocolo {} ---", lote.getId(), lote.getProtocolo());
            
            try {
                // Consultar estado del lote en SIFEN
                RespuestaConsultaLoteDE respuesta = Sifen.consultaLoteDE(lote.getProtocolo());
                logger.info("📥 Respuesta recibida - Código: {}, Mensaje: {}", 
                    respuesta.getdCodResLot(), respuesta.getdMsgResLot());
                
                // Procesar respuesta y actualizar estados
                procesarRespuestaConsultaLote(lote, respuesta);
                
            } catch (SifenException e) {
                logger.error("❌ Error al consultar lote {}: {}", lote.getId(), e.getMessage());
                lote.setEstado(EstadoLoteDE.ERROR_RED);
                loteDEService.save(lote);
            }
        }
        
        logger.info("✅ PASO 3 COMPLETADO: {} lotes consultados", lotesEnProceso.size());
        logger.info("=== FIN DE CONSULTA DE LOTES ===");
    }

    

    /**
     * Test para corregir los documentos de los lotes 14 y 15 que tienen estados incorrectos.
     */
    @Test
    @Transactional
    @Commit
    public void testCorregirDocumentosLotesExistentes() throws Exception {
        logger.info("=== TEST: CORRECCIÓN DE DOCUMENTOS Y LOTES EXISTENTES ===");
        
        // Buscar lotes 14 y 15
        LoteDE lote14 = loteDEService.findById(14L).orElse(null);
        LoteDE lote15 = loteDEService.findById(15L).orElse(null);
        
        if (lote14 != null && lote14.getRespuestaSifen() != null) {
            logger.info("🔧 Corrigiendo Lote 14 (debería ser APROBADO):");
            
            // Simular respuesta para el lote 14
            RespuestaConsultaLoteDE respuesta14 = new RespuestaConsultaLoteDE();
            respuesta14.setRespuestaBruta(lote14.getRespuestaSifen());
            
            boolean aprobado14 = determinarAprobacionLoteDesdeXML(respuesta14);
            com.franco.dev.domain.financiero.enums.EstadoDE estadoDoc14 = 
                aprobado14 ? com.franco.dev.domain.financiero.enums.EstadoDE.APROBADO : 
                           com.franco.dev.domain.financiero.enums.EstadoDE.RECHAZADO;
            
            logger.info("  - Estado calculado para documentos: {}", estadoDoc14);
            actualizarDocumentosLoteConDetalles(lote14, estadoDoc14, respuesta14);
        }
        
        if (lote15 != null && lote15.getRespuestaSifen() != null) {
            logger.info("🔧 Corrigiendo Lote 15 (debería ser RECHAZADO):");
            
            // Simular respuesta para el lote 15
            RespuestaConsultaLoteDE respuesta15 = new RespuestaConsultaLoteDE();
            respuesta15.setRespuestaBruta(lote15.getRespuestaSifen());
            
            boolean aprobado15 = determinarAprobacionLoteDesdeXML(respuesta15);
            com.franco.dev.domain.financiero.enums.EstadoDE estadoDoc15 = 
                aprobado15 ? com.franco.dev.domain.financiero.enums.EstadoDE.APROBADO : 
                           com.franco.dev.domain.financiero.enums.EstadoDE.RECHAZADO;
            
            logger.info("  - Estado calculado para documentos: {}", estadoDoc15);
            actualizarDocumentosLoteConDetalles(lote15, estadoDoc15, respuesta15);
        }
        
        logger.info("✅ Corrección de documentos completada");
    }
    
    /**
     * Test para investigar si SIFEN regenera los CDCs al enviar lotes.
     */
    @Test
    @Transactional
    @Commit
    public void testInvestigacionCDC() throws Exception {
        logger.info("=== INVESTIGACIÓN: ¿SIFEN REGENERA CDCs? ===");
        
        // IMPORTANTE: Crear una factura nueva para evitar duplicados
        // La factura 11866 ya tiene un DE, duplicarlo causaría problemas en SIFEN
        logger.info("🔧 Creando factura nueva para investigación (evitar duplicados)...");
        
        // Crear nueva factura para cliente 194 (RUC válido)
        FacturaLegal factura = new FacturaLegal();
        factura.setSucursalId(24L);
        factura.setFecha(LocalDateTime.now());
        factura.setCredito(false); // Contado
        
        // Cargar cliente válido
        Cliente cliente = clienteService.findById(194L).orElse(null);
        if (cliente == null) {
            throw new IllegalArgumentException("Cliente con ID 194 no encontrado");
        }
        factura.setCliente(cliente);
        
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
        logger.info("✅ Factura nueva creada con ID: {}", factura.getId());
        
        // Crear items para la factura
        List<FacturaLegalItem> items = crearItemsParaFactura(factura, 1);
        
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
        
        logger.info("📋 Factura de investigación - ID: {}, Cliente: {}, Documento: {}, Total: {}", 
            factura.getId(), 
            factura.getCliente() != null ? factura.getCliente().getId() : "N/A",
            factura.getCliente() != null && factura.getCliente().getPersona() != null ? 
                factura.getCliente().getPersona().getDocumento() : "N/A",
            factura.getTotalFinal());
        
        logger.info("📋 Factura cargada - ID: {}, Cliente: {}, Documento: {}", 
            factura.getId(), 
            factura.getCliente() != null ? factura.getCliente().getId() : "N/A",
            factura.getCliente() != null && factura.getCliente().getPersona() != null ? 
                factura.getCliente().getPersona().getDocumento() : "N/A");
        
        // Generar DE usando el método corregido
        DocumentoElectronico deSifen = generarDEDesdeFacturaDatosReales(factura);
        String cdcOriginal = deSifen.obtenerCDC();
        
        logger.info("🔍 CDC GENERADO ORIGINALMENTE: {}", cdcOriginal);
        
        // Aplicar fix para totales IVA
        aplicarFixTotalesIVA(deSifen.getgTotSub());
        
        // Crear lote con un solo DE
        List<DocumentoElectronico> loteDeUno = new ArrayList<>();
        loteDeUno.add(deSifen);
        
        logger.info("📤 Enviando lote a SIFEN...");
        RespuestaRecepcionLoteDE respuesta = Sifen.recepcionLoteDE(loteDeUno);
        
        logger.info("📥 Respuesta recibida:");
        logger.info("   - Código HTTP: {}", respuesta.getCodigoEstado());
        logger.info("   - Código respuesta: {}", respuesta.getdCodRes());
        logger.info("   - Mensaje: {}", respuesta.getdMsgRes());
        
        // VERIFICACIÓN CRÍTICA: ¿El DE original fue modificado?
        String cdcDespuesEnvio = deSifen.obtenerCDC();
        logger.info("🔍 CDC DESPUÉS DEL ENVÍO: {}", cdcDespuesEnvio);
        
        if (cdcOriginal.equals(cdcDespuesEnvio)) {
            logger.info("✅ CDC NO CAMBIÓ - SIFEN no regenera CDCs");
        } else {
            logger.error("❌ CDC CAMBIÓ - SIFEN regeneró el CDC!");
            logger.error("   Original:  {}", cdcOriginal);
            logger.error("   Después:   {}", cdcDespuesEnvio);
        }
        
        // Verificar si hay información en la respuesta bruta
        if (respuesta.getRespuestaBruta() != null) {
            logger.info("📋 Analizando respuesta bruta para CDCs...");
            String respuestaBruta = respuesta.getRespuestaBruta();
            
            // Buscar CDCs en la respuesta XML
            if (respuestaBruta.contains("Id>") && respuestaBruta.contains("</Id>")) {
                int startPos = 0;
                int cdcCount = 0;
                while ((startPos = respuestaBruta.indexOf("<Id>", startPos)) != -1) {
                    int endPos = respuestaBruta.indexOf("</Id>", startPos);
                    if (endPos > startPos) {
                        String cdcEnRespuesta = respuestaBruta.substring(startPos + 4, endPos);
                        cdcCount++;
                        logger.info("📄 CDC encontrado en respuesta {}: {}", cdcCount, cdcEnRespuesta);
                        
                        if (cdcEnRespuesta.equals(cdcOriginal)) {
                            logger.info("✅ CDC coincide con el original");
                        } else {
                            logger.warn("⚠️ CDC en respuesta difiere del original");
                        }
                    }
                    startPos = endPos;
                }
            }
        }
        
        logger.info("=== FIN DE INVESTIGACIÓN CDC ===");
    }

    /**
     * Test para verificar que dos DEs generados desde la misma factura son idénticos.
     */
    @Test
    @Transactional
    @Commit
    public void testDeterminismoDE() throws Exception {
        logger.info("=== TEST: DETERMINISMO DE DEs ===");
        
        // Crear una factura nueva para el test
        FacturaLegal factura = new FacturaLegal();
        factura.setSucursalId(24L);
        factura.setFecha(LocalDateTime.now());
        factura.setCredito(false);
        
        // Cargar cliente y timbrado
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
        
        // Número de factura
        Long siguienteNumero = timbradoDetalle.getNumeroActual() != null ? 
            timbradoDetalle.getNumeroActual() + 1 : 1L;
        factura.setNumeroFactura(siguienteNumero.intValue());
        
        // Guardar factura
        factura = facturaLegalService.save(factura);
        
        // Crear items
        List<FacturaLegalItem> items = crearItemsParaFactura(factura, 1);
        
        // Calcular totales
        double totalItems = items.stream()
            .mapToDouble(item -> item.getCantidad().floatValue() * item.getPrecioUnitario().doubleValue())
            .sum();
        double totalIva = totalItems * 0.10;
        double totalFinal = totalItems + totalIva;
        
        factura.setTotalFinal(totalFinal);
        factura = facturaLegalService.save(factura);
        
        // Actualizar timbrado
        timbradoDetalle.setNumeroActual(siguienteNumero);
        timbradoDetalleService.save(timbradoDetalle);
        
        logger.info("📋 Factura creada para test determinismo - ID: {}", factura.getId());
        
        // Generar DE dos veces con la misma factura
        logger.info("🔄 Generando DE por primera vez...");
        DocumentoElectronico de1 = generarDEDesdeFacturaDatosReales(factura);
        String cdc1 = de1.obtenerCDC();
        
        // Esperar un momento para asegurar que no hay diferencias de tiempo
        Thread.sleep(100);
        
        logger.info("🔄 Generando DE por segunda vez...");
        DocumentoElectronico de2 = generarDEDesdeFacturaDatosReales(factura);
        String cdc2 = de2.obtenerCDC();
        
        // Comparar resultados
        logger.info("🔍 CDC 1: {}", cdc1);
        logger.info("🔍 CDC 2: {}", cdc2);
        
        if (cdc1.equals(cdc2)) {
            logger.info("✅ DETERMINISMO CONFIRMADO - Los DEs son idénticos");
            logger.info("   Los CDCs son exactamente iguales: {}", cdc1.equals(cdc2));
        } else {
            logger.error("❌ PROBLEMA DE DETERMINISMO - Los DEs son diferentes!");
            logger.error("   CDC 1: {}", cdc1);
            logger.error("   CDC 2: {}", cdc2);
            logger.error("   Diferencia: {}", cdc1.length() == cdc2.length() ? "Contenido" : "Longitud");
        }
        
        // Comparar otros elementos críticos
        logger.info("🔍 Comparando elementos críticos:");
        logger.info("   - Fecha firma 1: {}", de1.getdFecFirma());
        logger.info("   - Fecha firma 2: {}", de2.getdFecFirma());
        logger.info("   - Fechas iguales: {}", de1.getdFecFirma().equals(de2.getdFecFirma()));
        
        logger.info("   - Fecha emisión 1: {}", de1.getgDatGralOpe().getdFeEmiDE());
        logger.info("   - Fecha emisión 2: {}", de2.getgDatGralOpe().getdFeEmiDE());
        logger.info("   - Fechas emisión iguales: {}", 
            de1.getgDatGralOpe().getdFeEmiDE().equals(de2.getgDatGralOpe().getdFeEmiDE()));
        
        logger.info("=== FIN DE TEST DETERMINISMO ===");
    }

    /**
     * Test para verificar el análisis individual de documentos desde XML.
     */
    @Test
    public void testAnalisisIndividualDocumentosXML() throws Exception {
        logger.info("=== TEST: ANÁLISIS INDIVIDUAL DE DOCUMENTOS DESDE XML ===");
        
        // XML de ejemplo con múltiples documentos
        String xmlEjemplo = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
            "<env:Header/><env:Body>\n" +
            "<ns2:rResEnviConsLoteDe xmlns:ns2=\"http://ekuatia.set.gov.py/sifen/xsd\">\n" +
            "<ns2:dFecProc>2025-10-01T13:59:30-03:00</ns2:dFecProc>\n" +
            "<ns2:dCodResLot>0362</ns2:dCodResLot>\n" +
            "<ns2:dMsgResLot>Procesamiento de lote {1078608211750507942} concluido</ns2:dMsgResLot>\n" +
            "<ns2:gResProcLote>\n" +
            "<ns2:id>01800994825001001000003222025100110408471526</ns2:id>\n" +
            "<ns2:dEstRes>Aprobado</ns2:dEstRes>\n" +
            "<ns2:dProtAut>2639347316</ns2:dProtAut>\n" +
            "<ns2:gResProc>\n" +
            "<ns2:dCodRes>0260</ns2:dCodRes>\n" +
            "<ns2:dMsgRes>Aprobado</ns2:dMsgRes>\n" +
            "</ns2:gResProc>\n" +
            "</ns2:gResProcLote>\n" +
            "<ns2:gResProcLote>\n" +
            "<ns2:id>01800994825001001000003322025100113946907079</ns2:id>\n" +
            "<ns2:dEstRes>Rechazado</ns2:dEstRes>\n" +
            "<ns2:gResProc>\n" +
            "<ns2:dCodRes>1309</ns2:dCodRes>\n" +
            "<ns2:dMsgRes>Dígito Verificador del RUC del receptor incorrecto</ns2:dMsgRes>\n" +
            "</ns2:gResProc>\n" +
            "</ns2:gResProcLote>\n" +
            "</ns2:rResEnviConsLoteDe>\n" +
            "</env:Body></env:Envelope>";
        
        // Probar extracción de detalles
        List<DetalleDocumentoEnLote> detalles = extraerDetallesDocumentosDesdeXML(xmlEjemplo);
        
        logger.info("📋 Documentos extraídos: {}", detalles.size());
        
        for (DetalleDocumentoEnLote detalle : detalles) {
            logger.info("📄 CDC: {} | Estado: {} | Código: {} | Mensaje: {}", 
                detalle.cdc, detalle.estado, detalle.codigo, detalle.mensaje);
        }
        
        // Verificar que se extrajeron correctamente
        assert detalles.size() == 2 : "Deberían extraerse 2 documentos";
        
        DetalleDocumentoEnLote doc1 = detalles.get(0);
        assert "01800994825001001000003222025100110408471526".equals(doc1.cdc) : "CDC del primer documento incorrecto";
        assert "Aprobado".equals(doc1.estado) : "Estado del primer documento debería ser Aprobado";
        
        DetalleDocumentoEnLote doc2 = detalles.get(1);
        assert "01800994825001001000003322025100113946907079".equals(doc2.cdc) : "CDC del segundo documento incorrecto";
        assert "Rechazado".equals(doc2.estado) : "Estado del segundo documento debería ser Rechazado";
        
        logger.info("✅ Test de análisis individual completado exitosamente");
    }

    /**
     * Crea items aleatorios para una factura específica.
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
        
        // Crear 2-3 items aleatorios por factura
        int numItems = 2 + (numeroFactura % 2); // 2 o 3 items
        
        for (int i = 0; i < numItems; i++) {
            FacturaLegalItem item = new FacturaLegalItem();
            item.setFacturaLegal(factura);
            item.setSucursalId(factura.getSucursalId());
            item.setDescripcion(productos[i % productos.length]);
            item.setCantidad(1.0f + i); // Cantidad variable
            item.setPrecioUnitario(precios[i % precios.length]);
            item.setTotal(item.getCantidad() * item.getPrecioUnitario());
            
            item = facturaLegalItemService.save(item);
            items.add(item);
            
            logger.info("✅ Item {} creado: {} x {} = {}", 
                i + 1, item.getDescripcion(), item.getCantidad(), item.getTotal());
        }
        
        return items;
    }

    /**
     * Crea un Documento Electrónico para una factura sin enviarlo a SIFEN.
     */
    private void crearDocumentoElectronicoParaFactura(FacturaLegal factura) throws Exception {
        com.franco.dev.domain.financiero.DocumentoElectronico de = 
            documentoElectronicoService.createFromFacturaLegal(factura);
        
        // CORREGIDO: Usar generarDEDesdeFacturaDatosReales que usa datos reales del cliente
        DocumentoElectronico deSifen = generarDEDesdeFacturaDatosReales(factura);
        
        // Asignar información del DE generado
        de.setCdc(deSifen.obtenerCDC());
        
        // CRÍTICO: Generar y guardar el XML original para poder reconstruir el DE exacto más tarde
        logger.info("🔧 Generando XML original del DE para almacenamiento...");
        String urlQr = null;
        try {
            com.roshka.sifen.internal.ctx.GenerationCtx ctx = com.roshka.sifen.internal.ctx.GenerationCtx.getDefaultFromConfig(sifenConfig);
            String xmlOriginal = deSifen.generarXml(ctx);
            de.setXmlOriginal(xmlOriginal);
            logger.info("✅ XML original generado y guardado ({} caracteres)", xmlOriginal.length());
            
            // OPTIMIZACIÓN: Extraer URL QR directamente del XML en lugar de generarlo localmente
            logger.info("🔧 Extrayendo URL QR del XML original...");
            urlQr = extraerUrlQrDesdeXml(xmlOriginal);
            
            if (urlQr != null) {
                logger.info("✅ URL QR extraída del XML correctamente");
            } else {
                logger.warn("⚠️  No se pudo extraer URL QR del XML, generando localmente...");
                throw new RuntimeException("No se pudo extraer URL QR del XML");
            }
        } catch (Exception e) {
            logger.error("❌ Error al generar XML original: {}", e.getMessage());
            logger.warn("⚠️  Continuando sin XML (se usará método de forzar CDC en su lugar)");
            // Fallback: Generar URL QR localmente si no hay XML
            throw new RuntimeException("Error al generar XML original");
        }
        
        de.setUrlQr(urlQr);
        de.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.PENDIENTE);
        
        // Guardar en BD
        documentoElectronicoService.save(de);
        
        logger.info("✅ DE creado para factura {} - CDC: {}", factura.getId(), de.getCdc());
    }

    /**
     * Extrae la URL del QR desde el XML del DE usando el helper SifenXmlParser.
     * La URL está en: gCamFuFD > dCarQR
     */
    private String extraerUrlQrDesdeXml(String xml) {
        String urlQr = com.franco.dev.service.sifen.util.SifenXmlParser.extractUrlQr(xml);
        
        if (urlQr != null) {
            logger.info("✅ URL QR extraída del XML ({} caracteres)", urlQr.length());
        } else {
            logger.warn("⚠️  No se pudo extraer URL QR del XML");
        }
        
        return urlQr;
    }

    /**
     * Método de fallback para reconstruir un DE cuando no hay XML original disponible.
     * Regenera el DE desde la factura y fuerza el CDC original.
     */
    private DocumentoElectronico reconstruirDEConFallback(com.franco.dev.domain.financiero.DocumentoElectronico de) throws Exception {
        // 1. Regenerar el objeto DE de SIFEN desde la factura
        DocumentoElectronico deSifen = generarDEDesdeFacturaDatosReales(de.getFacturaLegal());
        
        // 2. CRÍTICO: Sobrescribir el CDC con el valor original
        String cdcRegenerado = deSifen.obtenerCDC();
        logger.info("     CDC Regenerado: {}", cdcRegenerado);
        
        if (!de.getCdc().equals(cdcRegenerado)) {
            logger.warn("     ⚠️  El CDC regenerado es DIFERENTE al original. Forzando CDC original...");
            deSifen.setId(de.getCdc());
        } else {
            logger.info("     ✅ El CDC regenerado coincide con el original.");
        }
        
        return deSifen;
    }

    /**
     * Divide una lista de DEs en lotes más pequeños.
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

    /**
     * Extrae el protocolo de la respuesta XML de SIFEN.
     * Copiado de SifenDETest.java
     */
    private String extraerProtocoloDeRespuesta(String respuestaXml) {
        if (respuestaXml == null) {
            logger.warn("⚠️ RespuestaXml es null, no se puede extraer protocolo");
            return null;
        }
        
        try {
            int startProtocolo = respuestaXml.indexOf("<ns2:dProtConsLote>") + 19;
            int endProtocolo = respuestaXml.indexOf("</ns2:dProtConsLote>");
            
            if (startProtocolo > 19 && endProtocolo > startProtocolo) {
                String protocolo = respuestaXml.substring(startProtocolo, endProtocolo);
                logger.info("✅ Protocolo extraído del XML: {}", protocolo);
                return protocolo;
            } else {
                logger.warn("⚠️ No se encontró tag <ns2:dProtConsLote> en la respuesta XML");
                return null;
            }
        } catch (Exception e) {
            logger.error("❌ Error al extraer protocolo del XML: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Procesa la respuesta de consulta de lote y actualiza estados.
     * Copiado de SifenDETest.java
     */
    private void procesarRespuestaConsultaLote(LoteDE lote, RespuestaConsultaLoteDE respuesta) {
        logger.info("=== PROCESANDO RESPUESTA DE CONSULTA DE LOTE ===");
        logger.info("Lote ID: {}, Protocolo: {}", lote.getId(), lote.getProtocolo());
        
        // Actualizar información básica del lote
        lote.setFechaUltimoIntento(LocalDateTime.now());
        lote.setRespuestaSifen(respuesta.getRespuestaBruta());
        
        String codigoRespuesta = respuesta.getdCodResLot();
        String mensajeRespuesta = respuesta.getdMsgResLot();
        logger.info("Código de respuesta SIFEN (Lote): {}", codigoRespuesta);
        logger.info("Mensaje de respuesta SIFEN (Lote): {}", mensajeRespuesta);
        
        switch (codigoRespuesta) {
            case "0360": // No existe número de lote consultado
                logger.error("❌ Lote {} no existe en SIFEN. Código: {}, Mensaje: {}", 
                        lote.getId(), codigoRespuesta, mensajeRespuesta);
                lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                break;
                
            case "0361": // Lote en procesamiento
                logger.info("⏳ Lote {} aún en procesamiento en SIFEN. Código: {}, Mensaje: {}", 
                        lote.getId(), codigoRespuesta, mensajeRespuesta);
                lote.setEstado(EstadoLoteDE.EN_PROCESO);
                break;
                
            case "0362": // Procesamiento de lote concluido
                logger.info("✅ Lote {} procesamiento concluido por SIFEN. Código: {}, Mensaje: {}", 
                        lote.getId(), codigoRespuesta, mensajeRespuesta);
                logger.info("📋 Respuesta contiene contenedor del DE según manual técnico v150");
                
                // Determinar si el lote fue aprobado o rechazado analizando el XML
                boolean loteAprobado = determinarAprobacionLoteDesdeXML(respuesta);
                logger.info("🎯 Lote {} - Resultado: {}", lote.getId(), loteAprobado ? "APROBADO" : "RECHAZADO");
                
                // Actualizar estado del lote según el resultado real
                EstadoLoteDE nuevoEstadoLote = loteAprobado ? EstadoLoteDE.PROCESADO : EstadoLoteDE.RECHAZADO;
                lote.setEstado(nuevoEstadoLote);
                lote.setFechaProcesado(LocalDateTime.now());
                
                // Determinar estado de los documentos
                com.franco.dev.domain.financiero.enums.EstadoDE estadoDocumentos = 
                    loteAprobado ? com.franco.dev.domain.financiero.enums.EstadoDE.APROBADO : 
                                  com.franco.dev.domain.financiero.enums.EstadoDE.RECHAZADO;
                
                // Procesar información detallada de los documentos
                procesarDetallesDocumentosEnLote(respuesta);
                actualizarDocumentosLoteConDetalles(lote, estadoDocumentos, respuesta);
                break;
                
            default: // Otros códigos
                logger.warn("⚠️ Lote {} con código de respuesta inesperado: {}, Mensaje: {}", 
                        lote.getId(), codigoRespuesta, mensajeRespuesta);
                lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                break;
        }
        
        // Guardar cambios en el lote
        loteDEService.save(lote);
        logger.info("✅ Lote actualizado - Nuevo estado: {}", lote.getEstado());
    }

    /**
     * Actualiza los documentos de un lote con información detallada de SIFEN.
     * Usa análisis individual de cada documento desde el XML de respuesta.
     */
    private void actualizarDocumentosLoteConDetalles(LoteDE lote, com.franco.dev.domain.financiero.enums.EstadoDE nuevoEstado, RespuestaConsultaLoteDE respuesta) {
        logger.info("=== ACTUALIZANDO DOCUMENTOS DEL LOTE ===");
        
        // Buscar todos los documentos del lote
        List<com.franco.dev.domain.financiero.DocumentoElectronico> documentos = 
            documentoElectronicoService.findByLoteDe(lote);
        
        logger.info("Documentos encontrados en el lote: {}", documentos.size());
        
        // Contadores para estadísticas del lote
        int documentosAprobados = 0;
        int documentosRechazados = 0;
        int documentosConError = 0;
        
        for (com.franco.dev.domain.financiero.DocumentoElectronico documento : documentos) {
            logger.info("Procesando documento ID: {}, CDC: {}", documento.getId(), documento.getCdc());
            
            // NUEVO: Determinar estado individual basado en los detalles del XML
            com.franco.dev.domain.financiero.enums.EstadoDE estadoIndividual = determinarEstadoIndividualDesdeDetalles(documento, respuesta, nuevoEstado);
            
            documento.setEstado(estadoIndividual);
            documento.setFechaRecepcionSifen(LocalDateTime.now());
            documento.setCodigoRespuestaSifen(respuesta.getdCodResLot());
            documento.setMensajeRespuestaSifen(respuesta.getdMsgResLot());
            
            // Contar estados
            switch (estadoIndividual) {
                case APROBADO:
                    documentosAprobados++;
                    logger.info("✅ Documento {} aprobado", documento.getId());
                    break;
                case RECHAZADO:
                    documentosRechazados++;
                    logger.info("❌ Documento {} rechazado", documento.getId());
                    break;
                default:
                    documentosConError++;
                    logger.warn("⚠️ Documento {} con estado inesperado: {}", documento.getId(), estadoIndividual);
                    break;
            }
            
            documentoElectronicoService.save(documento);
        }
        
        logger.info("✅ Resumen de actualización:");
        logger.info("   - Documentos aprobados: {}", documentosAprobados);
        logger.info("   - Documentos rechazados: {}", documentosRechazados);
        logger.info("   - Documentos con error: {}", documentosConError);
        logger.info("   - Total procesados: {}", documentos.size());
    }

    /**
     * Procesa los detalles de los documentos contenidos en la respuesta del lote.
     * Analiza cada DE individualmente desde el XML de respuesta.
     */
    private void procesarDetallesDocumentosEnLote(RespuestaConsultaLoteDE respuesta) {
        logger.info("=== PROCESANDO DETALLES DE DOCUMENTOS EN LOTE ===");
        
        try {
            String respuestaBruta = respuesta.getRespuestaBruta();
            if (respuestaBruta == null || respuestaBruta.trim().isEmpty()) {
                logger.warn("⚠️ Respuesta bruta vacía, no se pueden procesar detalles");
                return;
            }
            
            // Extraer información individual de cada DE desde el XML
            List<DetalleDocumentoEnLote> detallesDocumentos = extraerDetallesDocumentosDesdeXML(respuestaBruta);
            
            if (detallesDocumentos.isEmpty()) {
                logger.info("ℹ️ No se encontraron detalles de documentos en la respuesta XML");
                return;
            }
            
            logger.info("📋 Encontrados {} documentos con detalles individuales:", detallesDocumentos.size());
            
            for (DetalleDocumentoEnLote detalle : detallesDocumentos) {
                logger.info("📄 CDC: {} | Estado: {} | Código: {} | Mensaje: {} | Protocolo: {}", 
                    detalle.cdc, detalle.estado, detalle.codigo, detalle.mensaje, detalle.protocoloAutorizacion);
            }
            
            // Almacenar los detalles para uso posterior (usando campo temporal)
            // Nota: RespuestaConsultaLoteDE no tiene setDetallesDocumentos, usaremos un Map temporal
            if (respuesta instanceof RespuestaConsultaLoteDE) {
                // Almacenar en un campo temporal para uso posterior
                detallesDocumentosTemporales = detallesDocumentos;
            }
            
        } catch (Exception e) {
            logger.error("❌ Error al procesar detalles de documentos en lote: {}", e.getMessage(), e);
        }
        
        logger.info("=== FIN DE PROCESAMIENTO DE DETALLES ===");
    }
    
    /**
     * Clase para almacenar detalles individuales de cada documento en el lote.
     */
    private static class DetalleDocumentoEnLote {
        String cdc;
        String estado;
        String codigo;
        String mensaje;
        String protocoloAutorizacion;
        
        public DetalleDocumentoEnLote(String cdc, String estado, String codigo, String mensaje, String protocoloAutorizacion) {
            this.cdc = cdc;
            this.estado = estado;
            this.codigo = codigo;
            this.mensaje = mensaje;
            this.protocoloAutorizacion = protocoloAutorizacion;
        }
    }
    
    /**
     * Extrae los detalles individuales de cada documento desde el XML de respuesta.
     * Busca todos los elementos <ns2:gResProcLote> y extrae la información de cada uno.
     */
    private List<DetalleDocumentoEnLote> extraerDetallesDocumentosDesdeXML(String xmlRespuesta) {
        List<DetalleDocumentoEnLote> detalles = new ArrayList<>();
        
        try {
            // Buscar todos los bloques gResProcLote
            String patronGResProcLote = "<ns2:gResProcLote>";
            int indiceInicio = 0;
            
            while (indiceInicio < xmlRespuesta.length()) {
                int inicioBloque = xmlRespuesta.indexOf(patronGResProcLote, indiceInicio);
                if (inicioBloque == -1) break;
                
                int finBloque = xmlRespuesta.indexOf("</ns2:gResProcLote>", inicioBloque);
                if (finBloque == -1) break;
                
                String bloqueDocumento = xmlRespuesta.substring(inicioBloque, finBloque + "</ns2:gResProcLote>".length());
                
                // Extraer información del bloque
                String cdc = extraerValorXML(bloqueDocumento, "<ns2:id>", "</ns2:id>");
                String estado = extraerValorXML(bloqueDocumento, "<ns2:dEstRes>", "</ns2:dEstRes>");
                String protocolo = extraerValorXML(bloqueDocumento, "<ns2:dProtAut>", "</ns2:dProtAut>");
                String codigo = extraerValorXML(bloqueDocumento, "<ns2:dCodRes>", "</ns2:dCodRes>");
                String mensaje = extraerValorXML(bloqueDocumento, "<ns2:dMsgRes>", "</ns2:dMsgRes>");
                
                if (cdc != null && !cdc.trim().isEmpty()) {
                    detalles.add(new DetalleDocumentoEnLote(cdc, estado, codigo, mensaje, protocolo));
                    logger.debug("✅ Documento extraído - CDC: {}, Estado: {}", cdc, estado);
                }
                
                indiceInicio = finBloque + "</ns2:gResProcLote>".length();
            }
            
        } catch (Exception e) {
            logger.error("❌ Error al extraer detalles de documentos desde XML: {}", e.getMessage(), e);
        }
        
        return detalles;
    }
    
    /**
     * Extrae un valor específico de un bloque XML.
     */
    private String extraerValorXML(String xml, String tagInicio, String tagFin) {
        try {
            int inicio = xml.indexOf(tagInicio);
            if (inicio == -1) return null;
            
            inicio += tagInicio.length();
            int fin = xml.indexOf(tagFin, inicio);
            if (fin == -1) return null;
            
            return xml.substring(inicio, fin).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Determina si un lote fue aprobado o rechazado analizando el XML de respuesta.
     * Busca el campo <ns2:dEstRes> en la respuesta XML.
     * 
     * @param respuesta La respuesta de consulta de lote de SIFEN
     * @return true si el lote fue aprobado, false si fue rechazado
     */
    private boolean determinarAprobacionLoteDesdeXML(RespuestaConsultaLoteDE respuesta) {
        try {
            String respuestaBruta = respuesta.getRespuestaBruta();
            if (respuestaBruta == null || respuestaBruta.trim().isEmpty()) {
                logger.warn("⚠️ Respuesta bruta vacía, asumiendo lote rechazado");
                return false;
            }
            
            // Buscar dEstRes en la respuesta XML
            if (respuestaBruta.contains("<ns2:dEstRes>")) {
                int startIndex = respuestaBruta.indexOf("<ns2:dEstRes>") + 13;
                int endIndex = respuestaBruta.indexOf("</ns2:dEstRes>");
                
                if (startIndex > 13 && endIndex > startIndex) {
                    String estadoResultado = respuestaBruta.substring(startIndex, endIndex).trim();
                    logger.info("📋 Estado del lote según SIFEN XML: {}", estadoResultado);
                    
                    // Aprobado si el estado es "Aprobado" (case insensitive)
                    boolean aprobado = "Aprobado".equalsIgnoreCase(estadoResultado);
                    logger.info("🎯 Lote {} por SIFEN", aprobado ? "APROBADO" : "RECHAZADO");
                    
                    return aprobado;
                }
            }
            
            logger.warn("⚠️ No se encontró dEstRes en la respuesta XML, asumiendo lote rechazado");
            return false;
            
        } catch (Exception e) {
            logger.error("❌ Error al determinar aprobación del lote desde XML: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Determina el estado individual de un documento basado en los detalles extraídos del XML.
     * Busca el CDC específico del documento en los detalles de la respuesta.
     */
    private com.franco.dev.domain.financiero.enums.EstadoDE determinarEstadoIndividualDesdeDetalles(
            com.franco.dev.domain.financiero.DocumentoElectronico documento, 
            RespuestaConsultaLoteDE respuesta,
            com.franco.dev.domain.financiero.enums.EstadoDE estadoFallback) {
        
        try {
            // Intentar obtener los detalles de documentos procesados
            List<DetalleDocumentoEnLote> detallesDocumentos = detallesDocumentosTemporales;
            
            if (detallesDocumentos != null && !detallesDocumentos.isEmpty()) {
                // Buscar el documento específico por CDC
                String cdcDocumento = documento.getCdc();
                
                for (DetalleDocumentoEnLote detalle : detallesDocumentos) {
                    if (cdcDocumento != null && cdcDocumento.equals(detalle.cdc)) {
                        logger.info("🎯 Encontrado detalle individual para CDC {}: Estado={}, Código={}, Mensaje={}", 
                            cdcDocumento, detalle.estado, detalle.codigo, detalle.mensaje);
                        
                        // Determinar estado basado en el estado individual del documento
                        if ("Aprobado".equalsIgnoreCase(detalle.estado)) {
                            return com.franco.dev.domain.financiero.enums.EstadoDE.APROBADO;
                        } else if ("Rechazado".equalsIgnoreCase(detalle.estado)) {
                            return com.franco.dev.domain.financiero.enums.EstadoDE.RECHAZADO;
                        } else {
                            logger.warn("⚠️ Estado desconocido para CDC {}: {}", cdcDocumento, detalle.estado);
                            return com.franco.dev.domain.financiero.enums.EstadoDE.RECHAZADO;
                        }
                    }
                }
                
                logger.warn("⚠️ No se encontró detalle individual para CDC {} en la respuesta", cdcDocumento);
            } else {
                logger.warn("⚠️ No hay detalles individuales disponibles, usando estado general del lote");
            }
            
            // Fallback: usar el estado general del lote si no se encuentran detalles individuales
            return estadoFallback;
            
        } catch (Exception e) {
            logger.error("❌ Error al determinar estado individual desde detalles: {}", e.getMessage(), e);
            return estadoFallback;
        }
    }

    /**
     * Determina el estado individual de un documento basado en la respuesta de SIFEN.
     * MÉTODO LEGACY - mantener por compatibilidad.
     */
    private com.franco.dev.domain.financiero.enums.EstadoDE determinarEstadoIndividualDocumento(
            com.franco.dev.domain.financiero.DocumentoElectronico documento, 
            RespuestaConsultaLoteDE respuesta) {
        
        String codigoRespuesta = respuesta.getdCodResLot();
        
        switch (codigoRespuesta) {
            case "0360": // No existe número de lote consultado
                return com.franco.dev.domain.financiero.enums.EstadoDE.RECHAZADO;
            case "0361": // Lote en procesamiento
                return com.franco.dev.domain.financiero.enums.EstadoDE.EN_LOTE;
            case "0362": // Procesamiento de lote concluido
                return com.franco.dev.domain.financiero.enums.EstadoDE.APROBADO;
            default:
                return com.franco.dev.domain.financiero.enums.EstadoDE.RECHAZADO;
        }
    }

    /**
     * Genera un DocumentoElectronico de SIFEN desde una FacturaLegal.
     * Método simplificado basado en SifenDETest.java
     */
    private DocumentoElectronico generarDEDesdeFactura(FacturaLegal factura) throws Exception {
        logger.info("Generando DE desde FacturaLegal ID: {}", factura.getId());
        
        LocalDateTime fechaFactura = factura.getFecha();
        
        // Grupo A
        DocumentoElectronico DE = new DocumentoElectronico();
        DE.setdFecFirma(fechaFactura);
        DE.setdSisFact((short) 1);

        // Grupo B
        com.roshka.sifen.core.fields.request.de.TgOpeDE gOpeDE = new com.roshka.sifen.core.fields.request.de.TgOpeDE();
        gOpeDE.setiTipEmi(com.roshka.sifen.core.types.TTipEmi.NORMAL);
        DE.setgOpeDE(gOpeDE);

        // Grupo C - Datos del Timbrado
        com.roshka.sifen.core.fields.request.de.TgTimb gTimb = new com.roshka.sifen.core.fields.request.de.TgTimb();
        gTimb.setiTiDE(com.roshka.sifen.core.types.TTiDE.FACTURA_ELECTRONICA);
        gTimb.setdNumTim(Integer.parseInt(factura.getTimbradoDetalle().getTimbrado().getNumero()));
        gTimb.setdEst("001");
        gTimb.setdPunExp(factura.getTimbradoDetalle().getPuntoExpedicion());
        gTimb.setdNumDoc(String.format("%07d", factura.getNumeroFactura()));
        gTimb.setdFeIniT(factura.getTimbradoDetalle().getTimbrado().getFechaInicio().toLocalDate());
        DE.setgTimb(gTimb);

        // Grupo D - Datos generales (simplificado para el test)
        com.roshka.sifen.core.fields.request.de.TdDatGralOpe dDatGralOpe = new com.roshka.sifen.core.fields.request.de.TdDatGralOpe();
        dDatGralOpe.setdFeEmiDE(fechaFactura);

        com.roshka.sifen.core.fields.request.de.TgOpeCom gOpeCom = new com.roshka.sifen.core.fields.request.de.TgOpeCom();
        gOpeCom.setiTipTra(com.roshka.sifen.core.types.TTipTra.VENTA_MERCADERIA);
        gOpeCom.setiTImp(com.roshka.sifen.core.types.TTImp.IVA);
        gOpeCom.setcMoneOpe(com.roshka.sifen.core.types.CMondT.PYG);
        dDatGralOpe.setgOpeCom(gOpeCom);

        // Emisor (simplificado)
        com.roshka.sifen.core.fields.request.de.TgEmis gEmis = new com.roshka.sifen.core.fields.request.de.TgEmis();
        String rucCompleto = factura.getTimbradoDetalle().getTimbrado().getRuc();
        String[] rucPartes = rucCompleto.split("-");
        gEmis.setdRucEm(rucPartes[0]);
        gEmis.setdDVEmi(rucPartes.length > 1 ? rucPartes[1] : "");
        gEmis.setiTipCont(com.roshka.sifen.core.types.TiTipCont.PERSONA_JURIDICA);
        gEmis.setdNomEmi(factura.getTimbradoDetalle().getTimbrado().getRazonSocial());
        gEmis.setdDirEmi(factura.getTimbradoDetalle().getDireccion());
        gEmis.setdNumCas("0");
        gEmis.setcDepEmi(com.roshka.sifen.core.types.TDepartamento.CANINDEYU);
        // gEmis.setcCiuEmi(CodigosGeograficos.Ciudad.SALTO_DEL_GUAIRA.getCodigo());
        gEmis.setdDesCiuEmi("SALTO DEL GUAIRA");
        gEmis.setdTelEmi(factura.getTimbradoDetalle().getTelefono());
        gEmis.setdEmailE(factura.getTimbradoDetalle().getTimbrado().getEmail());

        // Actividades económicas
        List<com.roshka.sifen.core.fields.request.de.TgActEco> gActEcoList = new ArrayList<>();
        com.roshka.sifen.core.fields.request.de.TgActEco gActEco = new com.roshka.sifen.core.fields.request.de.TgActEco();
        gActEco.setcActEco("46304");
        gActEco.setdDesActEco("COMERCIO AL POR MAYOR DE BEBIDAS");
        gActEcoList.add(gActEco);
        gEmis.setgActEcoList(gActEcoList);
        dDatGralOpe.setgEmis(gEmis);

        // Receptor (simplificado)
        com.roshka.sifen.core.fields.request.de.TgDatRec gDatRec = new com.roshka.sifen.core.fields.request.de.TgDatRec();
        gDatRec.setiNatRec(com.roshka.sifen.core.types.TiNatRec.CONTRIBUYENTE);
        gDatRec.setiTiOpe(com.roshka.sifen.core.types.TiTiOpe.B2C);
        gDatRec.setcPaisRec(com.roshka.sifen.core.types.PaisType.PRY);
        gDatRec.setiTiContRec(com.roshka.sifen.core.types.TiTipCont.PERSONA_FISICA);
        gDatRec.setiTipIDRec(com.roshka.sifen.core.types.TiTipDocRec.CEDULA_PARAGUAYA);
        
        String rucSinDV = "5364471";
        int dvInt = CalcularVerificadorRuc.getDigitoVerificador(rucSinDV);
        String dvCalculado = String.valueOf(dvInt);
        gDatRec.setdNumIDRec(rucSinDV);
        gDatRec.setdRucRec(rucSinDV);
        gDatRec.setdDVRec(Short.parseShort(dvCalculado));
        gDatRec.setdNomRec("PERALTA CRISTIAN RAFAEL");
        
        dDatGralOpe.setgDatRec(gDatRec);
        DE.setgDatGralOpe(dDatGralOpe);

        // Grupo E - Items y condiciones
        com.roshka.sifen.core.fields.request.de.TgDtipDE gDtipDE = new com.roshka.sifen.core.fields.request.de.TgDtipDE();

        com.roshka.sifen.core.fields.request.de.TgCamFE gCamFE = new com.roshka.sifen.core.fields.request.de.TgCamFE();
        gCamFE.setiIndPres(com.roshka.sifen.core.types.TiIndPres.OPERACION_ELECTRONICA);
        gDtipDE.setgCamFE(gCamFE);

        com.roshka.sifen.core.fields.request.de.TgCamCond gCamCond = new com.roshka.sifen.core.fields.request.de.TgCamCond();
        gCamCond.setiCondOpe(com.roshka.sifen.core.types.TiCondOpe.CONTADO);

        List<com.roshka.sifen.core.fields.request.de.TgPaConEIni> gPaConEIniList = new ArrayList<>();
        com.roshka.sifen.core.fields.request.de.TgPaConEIni gPaConEIni = new com.roshka.sifen.core.fields.request.de.TgPaConEIni();
        gPaConEIni.setiTiPago(com.roshka.sifen.core.types.TiTiPago.EFECTIVO);
        gPaConEIni.setcMoneTiPag(com.roshka.sifen.core.types.CMondT.PYG);
        gPaConEIni.setdMonTiPag(BigDecimal.valueOf(factura.getTotalFinal()));
        gPaConEIniList.add(gPaConEIni);
        gCamCond.setgPaConEIniList(gPaConEIniList);
        gDtipDE.setgCamCond(gCamCond);

        // Items de la factura
        List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(factura.getId());
        List<com.roshka.sifen.core.fields.request.de.TgCamItem> gCamItemList = new ArrayList<>();
        
        for (int i = 0; i < items.size(); i++) {
            FacturaLegalItem item = items.get(i);
            com.roshka.sifen.core.fields.request.de.TgCamItem gCamItem = new com.roshka.sifen.core.fields.request.de.TgCamItem();
            gCamItem.setdCodInt(String.format("%03d", i + 1));
            gCamItem.setdDesProSer(item.getDescripcion());
            gCamItem.setcUniMed(com.roshka.sifen.core.types.TcUniMed.UNI);
            gCamItem.setdCantProSer(BigDecimal.valueOf(item.getCantidad().doubleValue()));

            com.roshka.sifen.core.fields.request.de.TgValorItem gValorItem = new com.roshka.sifen.core.fields.request.de.TgValorItem();
            gValorItem.setdPUniProSer(BigDecimal.valueOf(item.getPrecioUnitario().doubleValue()));

            com.roshka.sifen.core.fields.request.de.TgValorRestaItem gValorRestaItem = new com.roshka.sifen.core.fields.request.de.TgValorRestaItem();
            gValorItem.setgValorRestaItem(gValorRestaItem);
            gCamItem.setgValorItem(gValorItem);

            com.roshka.sifen.core.fields.request.de.TgCamIVA gCamIVA = new com.roshka.sifen.core.fields.request.de.TgCamIVA();
            gCamIVA.setiAfecIVA(com.roshka.sifen.core.types.TiAfecIVA.GRAVADO);
            gCamIVA.setdPropIVA(BigDecimal.valueOf(100));
            gCamIVA.setdTasaIVA(BigDecimal.valueOf(10));
            gCamItem.setgCamIVA(gCamIVA);

            gCamItemList.add(gCamItem);
        }

        gDtipDE.setgCamItemList(gCamItemList);
        DE.setgDtipDE(gDtipDE);

        // Grupo F - Totales
        DE.setgTotSub(new com.roshka.sifen.core.fields.request.de.TgTotSub());
        
        // Aplicar fix de totales IVA
        aplicarFixTotalesIVA(DE.getgTotSub());
        
        logger.info("DE generado correctamente con {} items", items.size());
        return DE;
    }

    /**
     * Aplica fix para bug de totales IVA en la librería.
     * Copiado de SifenDETest.java
     */
    private void aplicarFixTotalesIVA(com.roshka.sifen.core.fields.request.de.TgTotSub totales) {
        try {
            // Fix para IVA 10%
            if (totales.getdIVA10() != null && totales.getdIVA10().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal valorIVA10 = totales.getdIVA10();
                BigDecimal valorActualLiq10 = totales.getdLiqTotIVA10();
                
                if (valorActualLiq10 == null || valorActualLiq10.compareTo(BigDecimal.ZERO) == 0) {
                    Field field = com.roshka.sifen.core.fields.request.de.TgTotSub.class.getDeclaredField("dLiqTotIVA10");
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
                    Field field = com.roshka.sifen.core.fields.request.de.TgTotSub.class.getDeclaredField("dLiqTotIVA5");
                    field.setAccessible(true);
                    field.set(totales, valorIVA5);
                    logger.info("✅ WORKAROUND aplicado: dLiqTotIVA5 corregido de {} a {}", 
                        valorActualLiq5, valorIVA5);
                }
            }
            
            logger.info("✅ Fix de totales IVA aplicado exitosamente");
            
        } catch (Exception e) {
            logger.error("❌ Error al aplicar workaround de totales IVA: {}", e.getMessage());
        }
    }
    
    /**
     * Genera un DocumentoElectronico de SIFEN desde una FacturaLegal usando datos reales del proyecto
     * Copiado de SifenDETest.java
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
        DE.setdFecFirma(factura.getCreadoEn());
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
        boolean esContribuyente = factura.getCliente().getTributa() == null || factura.getCliente().getTributa();
            
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
     * Copiado de SifenDETest.java
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
     * Mapea el nombre del departamento a su enum correspondiente.
     * Copiado de SifenDETest.java
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
}
