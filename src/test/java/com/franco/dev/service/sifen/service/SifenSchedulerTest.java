package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class SifenSchedulerTest {

    private static final Logger log = LoggerFactory.getLogger(SifenSchedulerTest.class);


    @Autowired
    private SifenService sifenService;

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    @Autowired
    private LoteDEService loteDEService;

    @Test
    @Transactional
    @Commit
    void testProcesamientoCompletoScheduler() {
        log.info("=== INICIANDO TEST DE PROCESAMIENTO COMPLETO DEL SCHEDULER ===");
        
        try {
            // Paso 1: Verificar documentos pendientes existentes
            log.info("=== PASO 1: VERIFICANDO DOCUMENTOS PENDIENTES ===");
            List<DocumentoElectronico> documentosPendientes = documentoElectronicoService.findByEstado(EstadoDE.PENDIENTE);
            log.info("Documentos pendientes encontrados: {}", documentosPendientes.size());
            
            if (documentosPendientes.isEmpty()) {
                log.warn("⚠️  No hay documentos pendientes para procesar");
                log.warn("Este test requiere que existan documentos electrónicos en estado PENDIENTE");
                log.warn("Ejecute primero el test VentaGraphQLTestConLogs para crear documentos pendientes");
                return;
            }
            
            // Mostrar información de los documentos pendientes
            for (DocumentoElectronico doc : documentosPendientes) {
                log.info("Documento pendiente - ID: {}, CDC: {}, Factura: {}", 
                    doc.getId(), doc.getCdc(), doc.getFacturaLegal().getId());
            }
            
            // Paso 2: Crear lotes con documentos pendientes
            log.info("=== PASO 2: CREANDO LOTES CON DOCUMENTOS PENDIENTES ===");
            sifenService.crearLotesConDocumentosPendientes();
            
            // Verificar que se crearon lotes
            List<LoteDE> lotesCreados = loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.PENDIENTE_ENVIO);
            log.info("Lotes creados en estado PENDIENTE_ENVIO: {}", lotesCreados.size());
            
            assertFalse(lotesCreados.isEmpty(), "Debe haberse creado al menos un lote");
            
            // Verificar que los documentos fueron asignados a lotes
            List<DocumentoElectronico> documentosEnLote = documentoElectronicoService.findByEstado(EstadoDE.EN_LOTE);
            log.info("Documentos asignados a lotes: {}", documentosEnLote.size());
            
            // Paso 3: Procesar lotes pendientes (enviar a SIFEN)
            log.info("=== PASO 3: PROCESANDO LOTES PENDIENTES ===");
            // Simular el procesamiento de lotes pendientes llamando directamente al método del scheduler
            procesarLotesPendientes();
            
            // Verificar el estado de los lotes después del procesamiento
            List<LoteDE> lotesProcesados = loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.EN_PROCESO);
            log.info("Lotes en estado EN_PROCESO: {}", lotesProcesados.size());
            
            List<LoteDE> lotesConError = obtenerLotesConErrores();
            log.info("Lotes con errores: {}", lotesConError.size());
            
            // Mostrar detalles de los lotes procesados
            for (LoteDE lote : lotesProcesados) {
                log.info("Lote procesado - ID: {}, Estado: {}, Documentos: {}, Intentos: {}", 
                    lote.getId(), lote.getEstado(), 
                    lote.getDocumentosElectronicos() != null ? lote.getDocumentosElectronicos().size() : 0,
                    lote.getIntentos());
            }
            
            // Mostrar detalles de los lotes con errores
            for (LoteDE lote : lotesConError) {
                log.info("Lote con error - ID: {}, Estado: {}, Respuesta: {}, Intentos: {}", 
                    lote.getId(), lote.getEstado(), lote.getRespuestaSifen(), lote.getIntentos());
            }
            
            // Paso 4: Consultar estado de lotes en proceso
            log.info("=== PASO 4: CONSULTANDO ESTADO DE LOTES EN PROCESO ===");
            procesarLotesEnProceso();
            
            // Verificar el estado final de los lotes
            List<LoteDE> lotesFinalizados = obtenerLotesFinalizados();
            log.info("Lotes finalizados: {}", lotesFinalizados.size());
            
            // Mostrar detalles de los lotes finalizados
            for (LoteDE lote : lotesFinalizados) {
                log.info("Lote finalizado - ID: {}, Estado: {}, Respuesta: {}", 
                    lote.getId(), lote.getEstado(), lote.getRespuestaSifen());
                
                // Mostrar estado de los documentos en el lote
                if (lote.getDocumentosElectronicos() != null) {
                    for (DocumentoElectronico doc : lote.getDocumentosElectronicos()) {
                        log.info("  Documento - ID: {}, Estado: {}, CDC: {}", 
                            doc.getId(), doc.getEstado(), doc.getCdc());
                    }
                }
            }
            
            // Paso 5: Procesar lotes con errores (reintentos)
            log.info("=== PASO 5: PROCESANDO LOTES CON ERRORES ===");
            procesarLotesConErrores();
            
            // Verificar si se reintentaron lotes
            List<LoteDE> lotesReintentados = obtenerLotesParaReintento();
            log.info("Lotes pendientes de reintento: {}", lotesReintentados.size());
            
            log.info("=== TEST DE PROCESAMIENTO COMPLETO DEL SCHEDULER COMPLETADO ===");
            
        } catch (Exception e) {
            log.error("Error durante el test del scheduler: {}", e.getMessage(), e);
            throw new RuntimeException("Error en el test del scheduler", e);
        }
    }

    @Test
    @Transactional
    @Commit
    void testCreacionLotesEspecifica() {
        log.info("=== INICIANDO TEST DE CREACIÓN ESPECÍFICA DE LOTES ===");
        
        try {
            // Verificar documentos pendientes antes de crear lotes
            List<DocumentoElectronico> documentosPendientesAntes = documentoElectronicoService.findByEstado(EstadoDE.PENDIENTE);
            log.info("Documentos pendientes antes de crear lotes: {}", documentosPendientesAntes.size());
            
            // Crear lotes
            sifenService.crearLotesConDocumentosPendientes();
            
            // Verificar documentos pendientes después de crear lotes
            List<DocumentoElectronico> documentosPendientesDespues = documentoElectronicoService.findByEstado(EstadoDE.PENDIENTE);
            log.info("Documentos pendientes después de crear lotes: {}", documentosPendientesDespues.size());
            
            // Verificar documentos en lotes
            List<DocumentoElectronico> documentosEnLote = documentoElectronicoService.findByEstado(EstadoDE.EN_LOTE);
            log.info("Documentos asignados a lotes: {}", documentosEnLote.size());
            
            // Verificar lotes creados
            List<LoteDE> lotesCreados = loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.PENDIENTE_ENVIO);
            log.info("Lotes creados: {}", lotesCreados.size());
            
            // Mostrar detalles de cada lote creado
            for (LoteDE lote : lotesCreados) {
                log.info("Lote creado - ID: {}, Estado: {}, Documentos: {}, Fecha creación: {}", 
                    lote.getId(), lote.getEstado(), 
                    lote.getDocumentosElectronicos() != null ? lote.getDocumentosElectronicos().size() : 0,
                    lote.getCreadoEn());
                
                // Mostrar detalles de los documentos en el lote
                if (lote.getDocumentosElectronicos() != null) {
                    for (DocumentoElectronico doc : lote.getDocumentosElectronicos()) {
                        log.info("  Documento en lote - ID: {}, CDC: {}, Factura: {}", 
                            doc.getId(), doc.getCdc(), doc.getFacturaLegal().getId());
                    }
                }
            }
            
            // Verificar que se respetó el orden FIFO
            verificarOrdenFIFO(documentosEnLote);
            
            log.info("=== TEST DE CREACIÓN ESPECÍFICA DE LOTES COMPLETADO ===");
            
        } catch (Exception e) {
            log.error("Error durante el test de creación de lotes: {}", e.getMessage(), e);
            throw new RuntimeException("Error en el test de creación de lotes", e);
        }
    }

    @Test
    @Transactional
    @Commit
    void testEnvioLotesASifen() {
        log.info("=== INICIANDO TEST DE ENVÍO DE LOTES A SIFEN ===");
        
        try {
            // Verificar lotes pendientes de envío
            List<LoteDE> lotesPendientes = loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.PENDIENTE_ENVIO);
            log.info("Lotes pendientes de envío: {}", lotesPendientes.size());
            
            if (lotesPendientes.isEmpty()) {
                log.warn("⚠️  No hay lotes pendientes de envío");
                log.warn("Ejecute primero el test de creación de lotes");
                return;
            }
            
            // Procesar lotes pendientes (enviar a SIFEN)
            procesarLotesPendientes();
            
            // Verificar el resultado del envío
            List<LoteDE> lotesEnProceso = loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.EN_PROCESO);
            log.info("Lotes enviados exitosamente (EN_PROCESO): {}", lotesEnProceso.size());
            
            List<LoteDE> lotesConError = obtenerLotesConErrores();
            log.info("Lotes con errores: {}", lotesConError.size());
            
            // Mostrar detalles de los lotes enviados exitosamente
            for (LoteDE lote : lotesEnProceso) {
                log.info("Lote enviado exitosamente - ID: {}, Estado: {}, Fecha envío: {}, Respuesta: {}", 
                    lote.getId(), lote.getEstado(), lote.getFechaUltimoIntento(), lote.getRespuestaSifen());
            }
            
            // Mostrar detalles de los lotes con errores
            for (LoteDE lote : lotesConError) {
                log.info("Lote con error - ID: {}, Estado: {}, Error: {}, Intentos: {}", 
                    lote.getId(), lote.getEstado(), lote.getRespuestaSifen(), lote.getIntentos());
            }
            
            log.info("=== TEST DE ENVÍO DE LOTES A SIFEN COMPLETADO ===");
            
        } catch (Exception e) {
            log.error("Error durante el test de envío de lotes: {}", e.getMessage(), e);
            throw new RuntimeException("Error en el test de envío de lotes", e);
        }
    }

    @Test
    @Transactional
    @Commit
    void testEnviarUnicoDE() {
        log.info("=== INICIANDO TEST DE ENVÍO DE UN ÚNICO DE ===");
        Long specificDeId = 41L; // <--- CAMBIAR ESTE ID para probar un DE específico

        try {
            // Buscar el DE específico por ID
            Optional<DocumentoElectronico> deOpt = documentoElectronicoService.findById(specificDeId);
            if (!deOpt.isPresent()) {
                log.error("⚠️ No se encontró ningún DocumentoElectronico con el ID especificado: {}.", specificDeId);
                fail("No se encontró DE con ID " + specificDeId);
                return;
            }
            
            DocumentoElectronico deParaEnviar = deOpt.get();
            log.info("Enviando DE específico con ID: {}", specificDeId);

            Long deId = deParaEnviar.getId();
            log.info("Documento a enviar - ID: {}, Estado Actual: {}, CDC: {}",
                deParaEnviar.getId(), deParaEnviar.getEstado(), deParaEnviar.getCdc());

            // Llamar al método en SifenService para enviar el DE individualmente
            sifenService.enviarDE(deParaEnviar);

            // Verificar el estado del DocumentoElectronico después del envío
            DocumentoElectronico deActualizado = documentoElectronicoService.findById(deId)
                .orElseThrow(() -> new AssertionError("El documento electrónico ya no existe después del envío."));

            log.info("DE {} actualizado al estado: {}", deId, deActualizado.getEstado());
            log.info("Código de respuesta SIFEN: {}", deActualizado.getCodigoRespuestaSifen());
            log.info("Mensaje de respuesta SIFEN: {}", deActualizado.getMensajeRespuestaSifen());

            if (deActualizado.getEstado() == EstadoDE.RECHAZADO) {
                log.error("❌ El Documento Electrónico fue RECHAZADO.");
                log.error("Causa: {} - {}", deActualizado.getCodigoRespuestaSifen(), deActualizado.getMensajeRespuestaSifen());
            } else {
                log.info("✅ El Documento Electrónico fue procesado. Estado final: {}", deActualizado.getEstado());
            }

        } catch (Exception e) {
            log.error("Error catastrófico durante el test de envío único de DE: {}", e.getMessage(), e);
            fail("El test falló con una excepción inesperada: " + e.getMessage());
        }

        log.info("=== TEST DE ENVÍO DE UN ÚNICO DE COMPLETADO ===");
    }

    @Test
    @Transactional
    @Commit
    void testConsultarEstadoDE() {
        log.info("=== INICIANDO TEST DE CONSULTA DE ESTADO DE DE DESDE SIFEN ===");
        
        // Configuración: usar ID o CDC directamente
        Long deIdParaConsultar = 41L; // <--- CAMBIAR ESTE ID para buscar en BD, o null para usar CDC directo
        String cdcDirecto = "01800167422069002004234722025092713654015820"; // <--- CAMBIAR ESTE CDC para usar directamente, o null para buscar por ID
        
        String cdcParaConsultar = null;
        Long deIdMostrar = null;

        try {
            if (cdcDirecto != null && !cdcDirecto.isEmpty()) {
                // Usar CDC directamente
                cdcParaConsultar = cdcDirecto;
                log.info("Usando CDC proporcionado directamente: {}", cdcParaConsultar);
            } else if (deIdParaConsultar != null) {
                // Buscar el DE específico por ID
                Optional<DocumentoElectronico> deOpt = documentoElectronicoService.findById(deIdParaConsultar);
                if (!deOpt.isPresent()) {
                    log.error("⚠️ No se encontró ningún DocumentoElectronico con el ID especificado: {}.", deIdParaConsultar);
                    fail("No se encontró DE con ID " + deIdParaConsultar);
                    return;
                }
                
                DocumentoElectronico documento = deOpt.get();
                cdcParaConsultar = documento.getCdc();
                deIdMostrar = deIdParaConsultar;
                
                if (cdcParaConsultar == null || cdcParaConsultar.isEmpty()) {
                    log.error("⚠️ El DE con ID {} no tiene CDC válido para consultar.", deIdParaConsultar);
                    fail("El DE no tiene CDC válido");
                    return;
                }
                
                log.info("Consultando estado del DE ID: {} con CDC: {}", deIdParaConsultar, cdcParaConsultar);
            } else {
                log.error("⚠️ Debe proporcionar un ID de DE o un CDC directo para consultar.");
                fail("No se proporcionó ID ni CDC para consultar");
                return;
            }
            
            // Llamar al método de consulta de estado desde SIFEN
            SifenService.DocumentoElectronicoInfo resultado = sifenService.consultarEstadoDE(cdcParaConsultar);
            
            if (resultado == null) {
                log.error("❌ Error: No se pudo obtener respuesta de SIFEN para el CDC: {}", cdcParaConsultar);
                fail("No se pudo consultar el estado del DE desde SIFEN");
                return;
            }
            
            // Mostrar los resultados de la consulta
            log.info("=== RESULTADO DE LA CONSULTA A SIFEN ===");
            if (deIdMostrar != null) {
                log.info("DE ID: {}", deIdMostrar);
            }
            log.info("CDC consultado: {}", resultado.getCdc());
            log.info("Estado del documento: {}", resultado.getEstadoDocumento());
            log.info("Código de respuesta SIFEN: {}", resultado.getCodigoRespuesta());
            log.info("Mensaje de respuesta SIFEN: {}", resultado.getMensajeRespuesta());
            log.info("Procesamiento correcto: {}", resultado.isProcesamientoCorrecto());
            log.info("URL QR: {}", resultado.getUrlQr());
            
            // Verificar que se obtuvo una respuesta válida
            assertNotNull(resultado, "La respuesta de SIFEN no debe ser nula");
            assertNotNull(resultado.getCdc(), "El CDC debe estar presente en la respuesta");
            assertNotNull(resultado.getEstadoDocumento(), "El estado del documento debe estar presente");
            assertNotNull(resultado.getCodigoRespuesta(), "El código de respuesta debe estar presente");
            
            // Mostrar resultado final
            if (resultado.isProcesamientoCorrecto()) {
                log.info("✅ El documento electrónico está APROBADO en SIFEN");
            } else {
                log.warn("⚠️ El documento electrónico NO está aprobado en SIFEN");
                log.warn("Estado: {} - {}", resultado.getEstadoDocumento(), resultado.getMensajeRespuesta());
            }
            
        } catch (Exception e) {
            log.error("Error durante el test de consulta de estado: {}", e.getMessage(), e);
            fail("El test falló con una excepción: " + e.getMessage());
        }

        log.info("=== TEST DE CONSULTA DE ESTADO DE DE DESDE SIFEN COMPLETADO ===");
    }

    /**
     * Verifica que los documentos se procesaron en orden FIFO (First In, First Out).
     */
    private void verificarOrdenFIFO(List<DocumentoElectronico> documentosEnLote) {
        log.info("=== VERIFICANDO ORDEN FIFO ===");
        
        if (documentosEnLote.size() < 2) {
            log.info("No hay suficientes documentos para verificar orden FIFO");
            return;
        }
        
        // Verificar que los documentos están ordenados por ID (FIFO)
        boolean ordenCorrecto = true;
        for (int i = 1; i < documentosEnLote.size(); i++) {
            if (documentosEnLote.get(i).getId() < documentosEnLote.get(i-1).getId()) {
                ordenCorrecto = false;
                break;
            }
        }
        
        if (ordenCorrecto) {
            log.info("✅ Orden FIFO correcto: los documentos se procesaron en orden de creación");
        } else {
            log.warn("⚠️  Orden FIFO incorrecto: los documentos no se procesaron en orden de creación");
        }
        
        // Mostrar el orden de los documentos
        log.info("Orden de documentos en lotes:");
        for (DocumentoElectronico doc : documentosEnLote) {
            log.info("  Documento ID: {}, CDC: {}, Fecha creación: {}", 
                doc.getId(), doc.getCdc(), doc.getCreadoEn());
        }
    }

    /**
     * Simula el procesamiento de lotes pendientes (método privado del scheduler).
     */
    private void procesarLotesPendientes() {
        try {
            List<LoteDE> lotesPendientes = sifenService.obtenerLotesParaEnvio();
            
            if (lotesPendientes.isEmpty()) {
                log.info("No hay lotes pendientes para enviar.");
                return;
            }

            log.info("Encontrados {} lotes pendientes para enviar.", lotesPendientes.size());

            for (LoteDE lote : lotesPendientes) {
                try {
                    log.info("Enviando lote {} a SIFEN...", lote.getId());
                    sifenService.enviarLote(lote);
                    
                    // Pequeña pausa entre envíos para no saturar SIFEN
                    Thread.sleep(1000);
                    
                } catch (Exception e) {
                    log.error("Error al enviar lote {}: {}", lote.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error durante el procesamiento de lotes pendientes: {}", e.getMessage(), e);
        }
    }

    /**
     * Simula el procesamiento de lotes en proceso (método privado del scheduler).
     */
    private void procesarLotesEnProceso() {
        try {
            List<LoteDE> lotesEnProceso = sifenService.obtenerLotesEnProceso();
            
            if (lotesEnProceso.isEmpty()) {
                log.info("No hay lotes en proceso para consultar.");
                return;
            }

            log.info("Encontrados {} lotes en proceso para consultar.", lotesEnProceso.size());

            for (LoteDE lote : lotesEnProceso) {
                try {
                    log.info("Consultando estado del lote {} en SIFEN...", lote.getId());
                    sifenService.consultarResultadoLote(lote);
                    
                    // Pequeña pausa entre consultas
                    Thread.sleep(1000);
                    
                } catch (Exception e) {
                    log.error("Error al consultar lote {}: {}", lote.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error durante el procesamiento de lotes en proceso: {}", e.getMessage(), e);
        }
    }

    /**
     * Simula el procesamiento de lotes con errores (método privado del scheduler).
     */
    private void procesarLotesConErrores() {
        try {
            List<LoteDE> lotesConErrores = sifenService.obtenerLotesParaReintento();
            
            if (lotesConErrores.isEmpty()) {
                log.info("No hay lotes con errores para reintentar.");
                return;
            }

            log.info("Encontrados {} lotes con errores para reintentar.", lotesConErrores.size());

            for (LoteDE lote : lotesConErrores) {
                try {
                    log.info("Reintentando lote {}...", lote.getId());
                    sifenService.enviarLote(lote);
                    
                    // Pequeña pausa entre reintentos
                    Thread.sleep(1000);
                    
                } catch (Exception e) {
                    log.error("Error al reintentar lote {}: {}", lote.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error durante el procesamiento de lotes con errores: {}", e.getMessage(), e);
        }
    }

    /**
     * Obtiene lotes con errores de diferentes tipos.
     */
    private List<LoteDE> obtenerLotesConErrores() {
        List<LoteDE> lotesConError = new ArrayList<>();
        
        // Obtener lotes con diferentes tipos de errores
        lotesConError.addAll(loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.ERROR_ENVIO));
        lotesConError.addAll(loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.ERROR_RED));
        lotesConError.addAll(loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.ERROR_PERMANENTE));
        
        return lotesConError;
    }

    /**
     * Obtiene lotes finalizados (procesados, con errores o rechazados).
     */
    private List<LoteDE> obtenerLotesFinalizados() {
        List<LoteDE> lotesFinalizados = new ArrayList<>();
        
        // Obtener lotes finalizados
        lotesFinalizados.addAll(loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.PROCESADO));
        lotesFinalizados.addAll(loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.PROCESADO_CON_ERRORES));
        lotesFinalizados.addAll(loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.RECHAZADO));
        
        return lotesFinalizados;
    }

    /**
     * Obtiene lotes pendientes de reintento.
     */
    private List<LoteDE> obtenerLotesParaReintento() {
        List<LoteDE> lotesParaReintento = new ArrayList<>();
        
        // Obtener lotes pendientes de reintento
        lotesParaReintento.addAll(loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.ERROR_ENVIO));
        lotesParaReintento.addAll(loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.ERROR_RED));
        
        return lotesParaReintento;
    }

    @Test
    @Transactional
    @Commit
    void testConsultaEstadoLote() {
        log.info("=== INICIANDO TEST DE CONSULTA DE ESTADO DE LOTE ===");
        try {
            // Buscar lotes en proceso para consultar
            List<LoteDE> lotesEnProceso = loteDEService.getRepository().findByEstadoOrderByCreadoEnAsc(EstadoLoteDE.EN_PROCESO);
            log.info("Lotes en proceso encontrados: {}", lotesEnProceso.size());
            
            if (lotesEnProceso.isEmpty()) {
                log.warn("⚠️  No hay lotes en proceso para consultar");
                log.warn("Este test requiere que existan lotes en estado EN_PROCESO");
                log.warn("Ejecute primero el test testEnvioLotesASifen para crear lotes en proceso");
                return;
            }
            
            // Consultar el estado de cada lote en proceso
            for (LoteDE lote : lotesEnProceso) {
                log.info("=== CONSULTANDO ESTADO DEL LOTE {} ===", lote.getId());
                log.info("Lote ID: {}", lote.getId());
                log.info("Estado actual: {}", lote.getEstado());
                log.info("Fecha procesado: {}", lote.getFechaProcesado());
                log.info("Fecha último intento: {}", lote.getFechaUltimoIntento());
                log.info("Número de intentos: {}", lote.getIntentos());
                
                // Consultar el estado del lote en SIFEN
                try {
                    sifenService.consultarResultadoLote(lote);
                    log.info("✅ Consulta de estado del lote {} completada exitosamente", lote.getId());
                    
                    // Verificar el estado actualizado del lote
                    Optional<LoteDE> loteActualizadoOpt = loteDEService.findById(lote.getId());
                    if (loteActualizadoOpt.isPresent()) {
                        LoteDE loteActualizado = loteActualizadoOpt.get();
                        log.info("Estado actualizado del lote: {}", loteActualizado.getEstado());
                        log.info("Fecha procesado: {}", loteActualizado.getFechaProcesado());
                        log.info("Respuesta SIFEN: {}", loteActualizado.getRespuestaSifen());
                        
                        // Verificar el estado de los documentos del lote
                        List<DocumentoElectronico> documentosLote = documentoElectronicoService.findByLoteDe(lote);
                        log.info("Documentos en el lote: {}", documentosLote.size());
                        
                        for (DocumentoElectronico documento : documentosLote) {
                            log.info("Documento ID: {}, Estado: {}, CDC: {}", 
                                    documento.getId(), documento.getEstado(), documento.getCdc());
                        }
                    } else {
                        log.warn("No se pudo encontrar el lote actualizado con ID: {}", lote.getId());
                    }
                    
                } catch (Exception e) {
                    log.error("❌ Error al consultar el estado del lote {}: {}", lote.getId(), e.getMessage(), e);
                    fail("Error al consultar el estado del lote: " + e.getMessage());
                }
            }
            
            log.info("=== TEST DE CONSULTA DE ESTADO DE LOTE COMPLETADO ===");
        } catch (Exception e) {
            log.error("Error durante el test de consulta de estado de lote: {}", e.getMessage(), e);
            fail("El test falló debido a una excepción: " + e.getMessage());
        }
    }
}
