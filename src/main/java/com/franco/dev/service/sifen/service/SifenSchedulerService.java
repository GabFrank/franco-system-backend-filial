package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio programado para el procesamiento automático de lotes de Documentos Electrónicos.
 * 
 * Funciones principales:
 * 0. MEJORA: Procesa lotes atrasados en PENDIENTE_ENVIO (recuperación de lotes huérfanos)
 * 1. Busca DEs con estado PENDIENTE
 * 2. Los agrupa en lotes (max 50 DEs por lote según SIFEN)
 * 3. Envía los lotes a SIFEN
 * 4. Espera 5 segundos para que SIFEN procese
 * 5. Consulta los lotes enviados para actualizar estados
 * 
 * MEJORAS IMPLEMENTADAS:
 * - Recuperación automática de lotes atrasados en PENDIENTE_ENVIO
 * - Manejo mejorado de errores que asegura que los lotes se marquen correctamente
 * - Validaciones para evitar procesar lotes sin documentos o que excedieron reintentos
 * - Logs mejorados para debugging y seguimiento
 * 
 * Configuración (application.properties):
 * - sifen.scheduler.enabled: Habilitar/deshabilitar scheduler (default: true)
 * - sifen.scheduler.fixed-delay: Intervalo entre ejecuciones en ms (default: 300000 = 5 min)
 * - sifen.lote.max-size: Máximo de DEs por lote (default: 50, máximo SIFEN)
 * - sifen.lote.max-retries: Máximo de reintentos por lote (default: 5)
 */
@Slf4j
@Service
@EnableScheduling
public class SifenSchedulerService {

    @Value("${sifen.scheduler.enabled:true}")
    private Boolean schedulerEnabled;
    
    @Value("${sifen.lote.max-size:50}")
    private Integer maxDocumentosPorLote;
    
    @Value("${sifen.lote.max-retries:5}")
    private Integer maxReintentos;

    private final SifenService sifenService;
    private final DocumentoElectronicoService documentoElectronicoService;
    private final LoteDEService loteDEService;
    
    // Flag para evitar ejecuciones concurrentes
    private volatile boolean procesandoLotes = false;

    public SifenSchedulerService(
            SifenService sifenService,
            DocumentoElectronicoService documentoElectronicoService,
            LoteDEService loteDEService) {
        this.sifenService = sifenService;
        this.documentoElectronicoService = documentoElectronicoService;
        this.loteDEService = loteDEService;
    }

    /**
     * Tarea programada principal que ejecuta el flujo completo de procesamiento de lotes.
     * 
     * Configuración:
     * - fixedDelayString: Lee el intervalo desde application.properties
     * - initialDelay: Espera 30 segundos después del inicio de la aplicación
     */
    @Scheduled(fixedDelayString = "${sifen.scheduler.fixed-delay:300000}", initialDelay = 30000)
    public void procesarLotesAutomaticamente() {
        // Verificar si el scheduler está habilitado
        if (!schedulerEnabled) {
            log.debug("Scheduler de SIFEN deshabilitado");
            return;
        }
        
        // Evitar ejecuciones concurrentes
        if (procesandoLotes) {
            log.warn("⚠️ Ejecución anterior aún en proceso - omitiendo esta ejecución");
            return;
        }
        
        try {
            procesandoLotes = true;
            log.info("=================================================================");
            log.info("🤖 INICIANDO PROCESAMIENTO AUTOMÁTICO DE LOTES DE SIFEN");
            log.info("   Fecha/Hora: {}", LocalDateTime.now());
            log.info("=================================================================");
            
            // PASO 0: Procesar lotes atrasados en PENDIENTE_ENVIO (MEJORA: recuperación de lotes huérfanos)
            log.info("\n🔧 PASO 0: Procesar lotes atrasados en PENDIENTE_ENVIO");
            procesarLotesAtrasados();
            
            // PASO 1: Crear y enviar lotes con DEs pendientes
            log.info("\n📦 PASO 1: Crear y enviar lotes con DEs pendientes");
            crearYEnviarLotes();
            
            // PASO 2: Esperar 5 segundos para que SIFEN procese los lotes
            log.info("\n⏳ PASO 2: Esperando 5 segundos para que SIFEN procese los lotes...");
            Thread.sleep(5000);
            
            // PASO 3: Consultar lotes en proceso
            log.info("\n🔍 PASO 3: Consultar lotes en proceso");
            consultarLotesPendientes();
            
            log.info("\n=================================================================");
            log.info("✅ PROCESAMIENTO AUTOMÁTICO COMPLETADO");
            log.info("=================================================================");
            
        } catch (InterruptedException e) {
            log.error("❌ Procesamiento interrumpido", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("❌ Error en procesamiento automático de lotes", e);
        } finally {
            procesandoLotes = false;
        }
    }

    /**
     * Crea lotes con todos los Documentos Electrónicos pendientes y los envía a SIFEN.
     * 
     * Lógica:
     * 1. Busca todos los DEs con estado PENDIENTE
     * 2. Los agrupa en lotes (máximo 50 por lote según SIFEN)
     * 3. Crea cada lote en BD
     * 4. Vincula los DEs al lote
     * 5. Envía el lote a SIFEN
     * 6. Actualiza el estado según la respuesta
     */
    @Transactional
    public void crearYEnviarLotes() {
        try {
            // 1. Buscar todos los DEs pendientes (sin lote asignado)
            List<com.franco.dev.domain.financiero.DocumentoElectronico> desPendientes = 
                documentoElectronicoService.findByEstado(EstadoDE.PENDIENTE);
            
            if (desPendientes.isEmpty()) {
                log.info("ℹ️  No hay DEs pendientes para procesar");
                return;
            }
            
            log.info("📋 Encontrados {} DEs pendientes para procesar", desPendientes.size());
            
            // 2. Agrupar DEs en lotes (máximo según configuración, default 50)
            List<List<com.franco.dev.domain.financiero.DocumentoElectronico>> lotes = 
                dividirEnLotes(desPendientes, maxDocumentosPorLote);
            
            log.info("📦 Se crearán {} lotes", lotes.size());
            
            // 3. Procesar cada lote
            int lotesEnviados = 0;
            int lotesConError = 0;
            
            for (int i = 0; i < lotes.size(); i++) {
                List<com.franco.dev.domain.financiero.DocumentoElectronico> loteDEs = lotes.get(i);
                log.info("\n--- Procesando lote {} de {} ({} DEs) ---", i + 1, lotes.size(), loteDEs.size());
                
                LoteDE lote = null;
                try {
                    // 3.1. Crear lote en BD
                    lote = sifenService.crearLote();
                    log.info("✅ Lote creado con ID: {}", lote.getId());
                    
                    // 3.2. Vincular DEs al lote
                    sifenService.vincularDocumentosALote(lote, loteDEs);
                    log.info("✅ {} DEs vinculados al lote", loteDEs.size());
                    
                    // 3.3. Enviar lote a SIFEN
                    sifenService.enviarLote(lote);
                    
                    // Verificar resultado del envío
                    LoteDE loteActualizado = loteDEService.findById(lote.getId()).orElse(null);
                    if (loteActualizado != null) {
                        if (loteActualizado.getEstado() == EstadoLoteDE.EN_PROCESO) {
                            log.info("✅ Lote {} enviado exitosamente - Protocolo: {}", 
                                lote.getId(), loteActualizado.getProtocolo());
                            lotesEnviados++;
                        } else {
                            log.error("❌ Lote {} con error al enviar - Estado: {}", 
                                lote.getId(), loteActualizado.getEstado());
                            lotesConError++;
                        }
                    }
                    
                } catch (SifenException e) {
                    log.error("❌ Error de SIFEN al procesar lote {} de {}: {}", 
                        i + 1, lotes.size(), e.getMessage(), e);
                    // MEJORA: Asegurar que el lote se marque con error si existe
                    if (lote != null) {
                        try {
                            LoteDE loteError = loteDEService.findById(lote.getId()).orElse(null);
                            if (loteError != null && loteError.getEstado() == EstadoLoteDE.PENDIENTE_ENVIO) {
                                loteError.setEstado(EstadoLoteDE.ERROR_ENVIO);
                                loteError.setFechaUltimoIntento(LocalDateTime.now());
                                loteDEService.save(loteError);
                                log.warn("⚠️  Lote {} marcado como ERROR_ENVIO después de excepción", lote.getId());
                            }
                        } catch (Exception ex) {
                            log.error("❌ Error al actualizar estado del lote {} después de excepción: {}", 
                                lote.getId(), ex.getMessage());
                        }
                    }
                    lotesConError++;
                } catch (Exception e) {
                    log.error("❌ Error inesperado al procesar lote {} de {}: {}", 
                        i + 1, lotes.size(), e.getMessage(), e);
                    // MEJORA: Asegurar que el lote se marque con error si existe
                    if (lote != null) {
                        try {
                            LoteDE loteError = loteDEService.findById(lote.getId()).orElse(null);
                            if (loteError != null && loteError.getEstado() == EstadoLoteDE.PENDIENTE_ENVIO) {
                                loteError.setEstado(EstadoLoteDE.ERROR_ENVIO);
                                loteError.setFechaUltimoIntento(LocalDateTime.now());
                                loteDEService.save(loteError);
                                log.warn("⚠️  Lote {} marcado como ERROR_ENVIO después de excepción inesperada", lote.getId());
                            }
                        } catch (Exception ex) {
                            log.error("❌ Error al actualizar estado del lote {} después de excepción inesperada: {}", 
                                lote.getId(), ex.getMessage());
                        }
                    }
                    lotesConError++;
                }
            }
            
            // 4. Resumen de procesamiento
            log.info("\n📊 RESUMEN DE ENVÍO DE LOTES:");
            log.info("   ✅ Lotes enviados exitosamente: {}", lotesEnviados);
            log.info("   ❌ Lotes con error: {}", lotesConError);
            log.info("   📋 Total procesados: {}", lotes.size());
            
        } catch (Exception e) {
            log.error("❌ Error al crear y enviar lotes", e);
        }
    }

    /**
     * Consulta todos los lotes con estado EN_PROCESO y actualiza sus estados.
     * 
     * Lógica:
     * 1. Busca lotes con estado EN_PROCESO
     * 2. Verifica que no excedan el máximo de reintentos
     * 3. Consulta el estado de cada lote en SIFEN
     * 4. Actualiza estados de lote y documentos según respuesta
     */
    @Transactional
    public void consultarLotesPendientes() {
        try {
            // 1. Buscar lotes en estado EN_PROCESO
            List<LoteDE> lotesEnProceso = loteDEService.findByEstado(EstadoLoteDE.EN_PROCESO);
            
            if (lotesEnProceso.isEmpty()) {
                log.info("ℹ️  No hay lotes en proceso para consultar");
                return;
            }
            
            log.info("📋 Encontrados {} lotes en proceso", lotesEnProceso.size());
            
            int lotesConsultados = 0;
            int lotesCompletados = 0;
            int lotesConError = 0;
            int lotesAunEnProceso = 0;
            
            // 2. Procesar cada lote
            for (LoteDE lote : lotesEnProceso) {
                log.info("\n--- Consultando lote {} (Protocolo: {}, Intento: {}/{}) ---", 
                    lote.getId(), lote.getProtocolo(), lote.getIntentos(), maxReintentos);
                
                try {
                    // Verificar límite de reintentos
                    if (lote.getIntentos() >= maxReintentos) {
                        log.warn("⚠️  Lote {} excedió el máximo de reintentos ({}) - marcando como ERROR_PERMANENTE", 
                            lote.getId(), maxReintentos);
                        lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                        loteDEService.save(lote);
                        lotesConError++;
                        continue;
                    }
                    
                    // Consultar estado del lote en SIFEN
                    sifenService.consultarLote(lote);
                    lotesConsultados++;
                    
                    // Verificar estado actualizado
                    LoteDE loteActualizado = loteDEService.findById(lote.getId()).orElse(null);
                    if (loteActualizado != null) {
                        switch (loteActualizado.getEstado()) {
                            case PROCESADO:
                            case PROCESADO_CON_ERRORES:
                                log.info("✅ Lote {} completado - Estado: {}", 
                                    lote.getId(), loteActualizado.getEstado());
                                lotesCompletados++;
                                break;
                            case EN_PROCESO:
                                log.info("⏳ Lote {} aún en procesamiento", lote.getId());
                                lotesAunEnProceso++;
                                break;
                            case ERROR_PERMANENTE:
                            case RECHAZADO:
                                log.error("❌ Lote {} con error - Estado: {}", 
                                    lote.getId(), loteActualizado.getEstado());
                                lotesConError++;
                                break;
                            default:
                                log.warn("⚠️  Lote {} con estado inesperado: {}", 
                                    lote.getId(), loteActualizado.getEstado());
                                break;
                        }
                    }
                    
                } catch (SifenException e) {
                    log.error("❌ Error de SIFEN al consultar lote {}: {}", 
                        lote.getId(), e.getMessage());
                    
                    // Incrementar contador de intentos
                    lote.setIntentos(lote.getIntentos() + 1);
                    lote.setFechaUltimoIntento(LocalDateTime.now());
                    
                    // Si excede reintentos, marcar como error
                    if (lote.getIntentos() >= maxReintentos) {
                        lote.setEstado(EstadoLoteDE.ERROR_RED);
                        log.error("❌ Lote {} marcado como ERROR_RED después de {} intentos", 
                            lote.getId(), lote.getIntentos());
                    }
                    
                    loteDEService.save(lote);
                    lotesConError++;
                    
                } catch (Exception e) {
                    log.error("❌ Error inesperado al consultar lote {}: {}", 
                        lote.getId(), e.getMessage(), e);
                    lotesConError++;
                }
            }
            
            // 3. Resumen de consultas
            log.info("\n📊 RESUMEN DE CONSULTA DE LOTES:");
            log.info("   ✅ Lotes completados: {}", lotesCompletados);
            log.info("   ⏳ Lotes aún en proceso: {}", lotesAunEnProceso);
            log.info("   ❌ Lotes con error: {}", lotesConError);
            log.info("   🔍 Total consultados: {}", lotesConsultados);
            
        } catch (Exception e) {
            log.error("❌ Error al consultar lotes pendientes", e);
        }
    }

    /**
     * Divide una lista de DEs en lotes más pequeños.
     * 
     * @param des Lista de documentos electrónicos
     * @param maxSize Tamaño máximo por lote
     * @return Lista de lotes (cada lote es una lista de DEs)
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
     * Procesa lotes que quedaron en estado PENDIENTE_ENVIO (lotes atrasados).
     * 
     * Este método recupera lotes que fueron creados pero nunca fueron enviados exitosamente,
     * posiblemente debido a errores durante el envío o interrupciones del sistema.
     * 
     * Lógica:
     * 1. Busca lotes con estado PENDIENTE_ENVIO
     * 2. Verifica que tengan documentos asociados
     * 3. Verifica que no excedan el máximo de reintentos
     * 4. Intenta enviar el lote nuevamente
     * 
     * MEJORA CRÍTICA: Esto resuelve el problema de lotes huérfanos que quedan sin procesar.
     */
    @Transactional
    public void procesarLotesAtrasados() {
        try {
            // 1. Buscar lotes en estado PENDIENTE_ENVIO
            List<LoteDE> lotesAtrasados = loteDEService.findByEstado(EstadoLoteDE.PENDIENTE_ENVIO);
            
            if (lotesAtrasados.isEmpty()) {
                log.info("ℹ️  No hay lotes atrasados para procesar");
                return;
            }
            
            log.info("📋 Encontrados {} lotes atrasados en PENDIENTE_ENVIO", lotesAtrasados.size());
            
            int lotesReenviados = 0;
            int lotesConError = 0;
            int lotesSinDocumentos = 0;
            int lotesExcedieronReintentos = 0;
            
            // 2. Procesar cada lote atrasado
            for (LoteDE lote : lotesAtrasados) {
                log.info("\n--- Procesando lote atrasado {} (Creado: {}, Intentos: {}/{}) ---", 
                    lote.getId(), lote.getCreadoEn(), lote.getIntentos(), maxReintentos);
                
                try {
                    // 2.1. Verificar que el lote tenga documentos
                    List<com.franco.dev.domain.financiero.DocumentoElectronico> documentos = 
                        documentoElectronicoService.findByLoteDe(lote);
                    
                    if (documentos.isEmpty()) {
                        log.warn("⚠️  Lote {} no tiene documentos asociados - marcando como ERROR_PERMANENTE", 
                            lote.getId());
                        lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                        loteDEService.save(lote);
                        lotesSinDocumentos++;
                        continue;
                    }
                    
                    log.info("   📄 Lote contiene {} documentos", documentos.size());
                    
                    // 2.2. Verificar límite de reintentos
                    if (lote.getIntentos() != null && lote.getIntentos() >= maxReintentos) {
                        log.warn("⚠️  Lote {} excedió el máximo de reintentos ({}) - marcando como ERROR_PERMANENTE", 
                            lote.getId(), maxReintentos);
                        lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                        lote.setFechaUltimoIntento(LocalDateTime.now());
                        loteDEService.save(lote);
                        lotesExcedieronReintentos++;
                        continue;
                    }
                    
                    // 2.3. Verificar edad del lote (opcional: solo procesar lotes con más de X tiempo)
                    // Si el lote fue creado hace menos de 1 minuto, podría estar en proceso, lo saltamos
                    if (lote.getCreadoEn() != null && 
                        lote.getCreadoEn().isAfter(LocalDateTime.now().minusMinutes(1))) {
                        log.debug("   ⏳ Lote {} es muy reciente (menos de 1 minuto) - saltando por ahora", 
                            lote.getId());
                        continue;
                    }
                    
                    // 2.4. Intentar enviar el lote nuevamente
                    log.info("   🔄 Reintentando envío del lote {}...", lote.getId());
                    sifenService.enviarLote(lote);
                    
                    // 2.5. Verificar resultado del reenvío
                    LoteDE loteActualizado = loteDEService.findById(lote.getId()).orElse(null);
                    if (loteActualizado != null) {
                        if (loteActualizado.getEstado() == EstadoLoteDE.EN_PROCESO) {
                            log.info("✅ Lote {} reenviado exitosamente - Protocolo: {}", 
                                lote.getId(), loteActualizado.getProtocolo());
                            lotesReenviados++;
                        } else {
                            log.error("❌ Lote {} con error al reenviar - Estado: {}", 
                                lote.getId(), loteActualizado.getEstado());
                            lotesConError++;
                        }
                    }
                    
                } catch (SifenException e) {
                    log.error("❌ Error de SIFEN al reenviar lote {}: {}", 
                        lote.getId(), e.getMessage());
                    
                    // Incrementar contador de intentos
                    if (lote.getIntentos() == null) {
                        lote.setIntentos(0);
                    }
                    lote.setIntentos(lote.getIntentos() + 1);
                    lote.setFechaUltimoIntento(LocalDateTime.now());
                    
                    // Si excede reintentos, marcar como error
                    if (lote.getIntentos() >= maxReintentos) {
                        lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                        log.error("❌ Lote {} marcado como ERROR_PERMANENTE después de {} intentos", 
                            lote.getId(), lote.getIntentos());
                    } else {
                        // Mantener en PENDIENTE_ENVIO para reintentar en siguiente ejecución
                        lote.setEstado(EstadoLoteDE.PENDIENTE_ENVIO);
                    }
                    
                    loteDEService.save(lote);
                    lotesConError++;
                    
                } catch (Exception e) {
                    log.error("❌ Error inesperado al reenviar lote {}: {}", 
                        lote.getId(), e.getMessage(), e);
                    
                    // Incrementar contador de intentos
                    if (lote.getIntentos() == null) {
                        lote.setIntentos(0);
                    }
                    lote.setIntentos(lote.getIntentos() + 1);
                    lote.setFechaUltimoIntento(LocalDateTime.now());
                    
                    // Si excede reintentos, marcar como error
                    if (lote.getIntentos() >= maxReintentos) {
                        lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                        log.error("❌ Lote {} marcado como ERROR_PERMANENTE después de {} intentos", 
                            lote.getId(), lote.getIntentos());
                    }
                    
                    loteDEService.save(lote);
                    lotesConError++;
                }
            }
            
            // 3. Resumen de procesamiento de lotes atrasados
            log.info("\n📊 RESUMEN DE PROCESAMIENTO DE LOTES ATRASADOS:");
            log.info("   ✅ Lotes reenviados exitosamente: {}", lotesReenviados);
            log.info("   ❌ Lotes con error: {}", lotesConError);
            log.info("   📭 Lotes sin documentos: {}", lotesSinDocumentos);
            log.info("   ⚠️  Lotes que excedieron reintentos: {}", lotesExcedieronReintentos);
            log.info("   📋 Total procesados: {}", lotesAtrasados.size());
            
        } catch (Exception e) {
            log.error("❌ Error al procesar lotes atrasados", e);
        }
    }
    
    /**
     * Método público para forzar una ejecución manual del scheduler.
     * Útil para pruebas o ejecuciones on-demand desde GraphQL.
     */
    public void ejecutarManualmente() {
        log.info("🔧 Ejecución manual del scheduler solicitada");
        procesarLotesAutomaticamente();
    }
}
