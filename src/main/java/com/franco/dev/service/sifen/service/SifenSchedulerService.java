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
import java.util.Arrays;
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
 * - Recuperación automática de lotes atrasados en PENDIENTE_ENVIO y ERROR_RED
 * - Manejo mejorado de errores que asegura que los lotes se marquen correctamente
 * - Validaciones para evitar procesar lotes sin documentos
 * - Sin límite de reintentos para recuperación automática
 * - Logs mejorados para debugging y seguimiento
 * 
 * Configuración (application.properties):
 * - sifen.scheduler.enabled: Habilitar/deshabilitar scheduler (default: true)
 * - sifen.scheduler.fixed-delay: Intervalo entre ejecuciones en ms (default: 300000 = 5 min)
 * - sifen.lote.max-size: Máximo de DEs por lote (default: 50, máximo SIFEN)
 * - sifen.lote.max-retries: Máximo de reintentos por lote (actualmente no se usa - sin límite)
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
            
            // PASO 0: Procesar lotes atrasados en PENDIENTE_ENVIO, ERROR_ENVIO o ERROR_RED (MEJORA: recuperación de lotes huérfanos)
            procesarLotesAtrasados();
            
            // PASO 1: Crear y enviar lotes con DEs pendientes
            crearYEnviarLotes();
            
            // PASO 2: Esperar 5 segundos para que SIFEN procese los lotes
            Thread.sleep(5000);
            
            // PASO 3: Consultar lotes en proceso
            consultarLotesPendientes();
            
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
                return;
            }
            
            // 2. Agrupar DEs en lotes (máximo según configuración, default 50)
            List<List<com.franco.dev.domain.financiero.DocumentoElectronico>> lotes = 
                dividirEnLotes(desPendientes, maxDocumentosPorLote);
            
            // 3. Procesar cada lote
            int lotesEnviados = 0;
            int lotesConError = 0;
            
            for (int i = 0; i < lotes.size(); i++) {
                List<com.franco.dev.domain.financiero.DocumentoElectronico> loteDEs = lotes.get(i);
                
                LoteDE lote = null;
                try {
                    // 3.1. Crear lote en BD
                    lote = sifenService.crearLote();
                    
                    // 3.2. Vincular DEs al lote
                    sifenService.vincularDocumentosALote(lote, loteDEs);
                    
                    // 3.3. Enviar lote a SIFEN
                    sifenService.enviarLote(lote);
                    
                    // Verificar resultado del envío
                    LoteDE loteActualizado = loteDEService.findById(lote.getId()).orElse(null);
                    if (loteActualizado != null) {
                        if (loteActualizado.getEstado() == EstadoLoteDE.EN_PROCESO) {
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
            
        } catch (Exception e) {
            log.error("❌ Error al crear y enviar lotes", e);
        }
    }

    /**
     * Consulta todos los lotes con estado EN_PROCESO y actualiza sus estados.
     * 
     * Lógica:
     * 1. Busca lotes con estado EN_PROCESO
     * 2. Consulta el estado de cada lote en SIFEN (sin límite de reintentos)
     * 3. Actualiza estados de lote y documentos según respuesta
     */
    @Transactional
    public void consultarLotesPendientes() {
        try {
            // 1. Buscar lotes en estado EN_PROCESO
            List<LoteDE> lotesEnProceso = loteDEService.findByEstado(EstadoLoteDE.EN_PROCESO);
            
            if (lotesEnProceso.isEmpty()) {
                return;
            }
            
            int lotesConsultados = 0;
            int lotesCompletados = 0;
            int lotesConError = 0;
            int lotesAunEnProceso = 0;
            
            // 2. Procesar cada lote
            for (LoteDE lote : lotesEnProceso) {
                try {
                    // MEJORA: Eliminada restricción de máximo de reintentos
                    // Se seguirá intentando consultar el lote independientemente del número de intentos
                    
                    // Consultar estado del lote en SIFEN
                    sifenService.consultarLote(lote);
                    lotesConsultados++;
                    
                    // Verificar estado actualizado
                    LoteDE loteActualizado = loteDEService.findById(lote.getId()).orElse(null);
                    if (loteActualizado != null) {
                        switch (loteActualizado.getEstado()) {
                            case PROCESADO:
                            case PROCESADO_CON_ERRORES:
                                lotesCompletados++;
                                break;
                            case EN_PROCESO:
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
                    
                    // Incrementar contador de intentos (verificar null)
                    if (lote.getIntentos() == null) {
                        lote.setIntentos(0);
                    }
                    lote.setIntentos(lote.getIntentos() + 1);
                    lote.setFechaUltimoIntento(LocalDateTime.now());
                    
                    // MEJORA: No marcamos como ERROR_RED por exceder reintentos
                    // El lote se mantiene en EN_PROCESO para seguir intentando
                    
                    loteDEService.save(lote);
                    lotesConError++;
                    
                } catch (Exception e) {
                    log.error("❌ Error inesperado al consultar lote {}: {}", 
                        lote.getId(), e.getMessage(), e);
                    
                    // Incrementar contador de intentos también en caso de error inesperado
                    if (lote.getIntentos() == null) {
                        lote.setIntentos(0);
                    }
                    lote.setIntentos(lote.getIntentos() + 1);
                    lote.setFechaUltimoIntento(LocalDateTime.now());
                    loteDEService.save(lote);
                    lotesConError++;
                }
            }
            
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
     * Procesa lotes que quedaron en estado PENDIENTE_ENVIO, ERROR_ENVIO o ERROR_RED (lotes atrasados).
     * 
     * Este método recupera lotes que fueron creados pero nunca fueron enviados exitosamente,
     * posiblemente debido a errores durante el envío, errores de red o interrupciones del sistema.
     * 
     * Lógica:
     * 1. Busca lotes con estado PENDIENTE_ENVIO, ERROR_ENVIO o ERROR_RED
     * 2. Verifica que tengan documentos asociados
     * 3. Intenta enviar el lote nuevamente (sin límite de reintentos)
     * 
     * MEJORAS:
     * - Incluye lotes con ERROR_ENVIO y ERROR_RED para recuperación automática
     * - Eliminada restricción de máximo de reintentos
     * - Esto resuelve el problema de lotes huérfanos que quedan sin procesar
     */
    @Transactional
    public void procesarLotesAtrasados() {
        try {
            // 1. Buscar lotes en estado PENDIENTE_ENVIO, ERROR_ENVIO o ERROR_RED
            // ERROR_ENVIO: falló el envío por problema de comunicación (se reintentará)
            // ERROR_RED: error de conectividad/red (se reintentará)
            // PENDIENTE_ENVIO: lote creado pero no enviado (se reintentará)
            List<EstadoLoteDE> estadosParaProcesar = Arrays.asList(
                EstadoLoteDE.PENDIENTE_ENVIO, 
                EstadoLoteDE.ERROR_ENVIO,
                EstadoLoteDE.ERROR_RED
            );
            List<LoteDE> lotesAtrasados = loteDEService.findByEstados(estadosParaProcesar);
            
            if (lotesAtrasados.isEmpty()) {
                return;
            }
            
            int lotesReenviados = 0;
            int lotesConError = 0;
            int lotesSinDocumentos = 0;
            
            // 2. Procesar cada lote atrasado
            for (LoteDE lote : lotesAtrasados) {
                log.info("\n--- Procesando lote atrasado {} (Creado: {}, Estado: {}, Intentos: {}) ---", 
                    lote.getId(), lote.getCreadoEn(), lote.getEstado(), lote.getIntentos());
                
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
                    
                    // 2.2. MEJORA: Eliminada restricción de máximo de reintentos
                    // Se seguirá intentando enviar el lote independientemente del número de intentos
                    
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
                    
                    // MEJORA: No marcamos como ERROR_PERMANENTE por exceder reintentos
                    // Mantener en estado original (PENDIENTE_ENVIO, ERROR_ENVIO o ERROR_RED) para reintentar en siguiente ejecución
                    EstadoLoteDE estadoOriginal = lote.getEstado();
                    if (estadoOriginal != EstadoLoteDE.ERROR_RED && 
                        estadoOriginal != EstadoLoteDE.ERROR_ENVIO && 
                        estadoOriginal != EstadoLoteDE.PENDIENTE_ENVIO) {
                        lote.setEstado(EstadoLoteDE.PENDIENTE_ENVIO);
                    }
                    // Si el estado es uno de los recuperables, se mantiene tal cual
                    
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
                    
                    // MEJORA: No marcamos como ERROR_PERMANENTE por exceder reintentos
                    // Mantener en estado original (PENDIENTE_ENVIO, ERROR_ENVIO o ERROR_RED) para reintentar en siguiente ejecución
                    EstadoLoteDE estadoOriginal = lote.getEstado();
                    if (estadoOriginal != EstadoLoteDE.ERROR_RED && 
                        estadoOriginal != EstadoLoteDE.ERROR_ENVIO && 
                        estadoOriginal != EstadoLoteDE.PENDIENTE_ENVIO) {
                        lote.setEstado(EstadoLoteDE.PENDIENTE_ENVIO);
                    }
                    // Si el estado es uno de los recuperables, se mantiene tal cual
                    
                    loteDEService.save(lote);
                    lotesConError++;
                }
            }
            
            // 3. Resumen de procesamiento de lotes atrasados
            log.info("\n📊 RESUMEN DE PROCESAMIENTO DE LOTES ATRASADOS:");
            log.info("   ✅ Lotes reenviados exitosamente: {}", lotesReenviados);
            log.info("   ❌ Lotes con error: {}", lotesConError);
            log.info("   📭 Lotes sin documentos: {}", lotesSinDocumentos);
            log.info("   📋 Total procesados: {}", lotesAtrasados.size());
            
        } catch (Exception e) {
            log.error("❌ Error al procesar lotes atrasados", e);
        }
    }
    
    /**
     * Procesa documentos electrónicos que tienen el mensaje de error específico sobre fecha adelantada,
     * los agrupa en lotes nuevos y los vuelve a consultar.
     * 
     * Este método busca todos los Documentos Electrónicos que tienen el mensaje:
     * "La fecha y hora de la firma digital es adelantada"
     * 
     * NOTA: Generalmente los lotes que contienen estos DEs rechazados se encuentran en estado
     * RECHAZADO o PROCESADO_CON_ERRORES. El método filtra específicamente estos casos.
     * 
     * Lógica:
     * 1. Busca todos los DEs con el mensaje específico
     * 2. Filtra solo los que pueden ser reprocesados:
     *    - Sin lote asignado, O
     *    - Con lote en estado final (RECHAZADO, PROCESADO_CON_ERRORES son los más comunes)
     * 3. Los agrupa en lotes (máximo según configuración, default 50)
     * 4. Crea un lote nuevo para cada grupo
     * 5. Resetea el estado de los documentos a PENDIENTE y limpia referencias a lotes anteriores
     * 6. Vincula los documentos al nuevo lote
     * 7. Envía el lote a SIFEN
     * 8. Consulta el lote para actualizar estados
     */
    @Transactional
    public void reprocesarDocumentosConFechaAdelantada() {
        try {
            String mensajeError = "La fecha y hora de la firma digital es adelantada";
            
            log.info("=================================================================");
            log.info("🔄 REPROCESANDO DOCUMENTOS CON FECHA ADELANTADA");
            log.info("   Mensaje buscado: {}", mensajeError);
            log.info("=================================================================");
            
            // 1. Buscar todos los DEs con el mensaje específico
            List<com.franco.dev.domain.financiero.DocumentoElectronico> todosLosDesConError = 
                documentoElectronicoService.findByMensajeRespuestaSifen(mensajeError);
            
            if (todosLosDesConError.isEmpty()) {
                log.info("ℹ️  No hay DEs con el mensaje '{}' para reprocesar", mensajeError);
                return;
            }
            
            log.info("📋 Encontrados {} DEs con fecha adelantada", todosLosDesConError.size());
            
            // 1.1. Filtrar solo los DEs que pueden ser reprocesados:
            // - Sin lote asignado, O
            // - Con lote en estado final (RECHAZADO, PROCESADO_CON_ERRORES son los más comunes)
            //   También incluye PROCESADO y ERROR_PERMANENTE por seguridad
            List<com.franco.dev.domain.financiero.DocumentoElectronico> desConError = new ArrayList<>();
            int sinLote = 0;
            int conLoteRechazado = 0;
            int conLoteProcesadoConErrores = 0;
            int conLoteOtroEstadoFinal = 0;
            int conLoteActivo = 0;
            
            for (com.franco.dev.domain.financiero.DocumentoElectronico de : todosLosDesConError) {
                if (de.getLoteDe() == null) {
                    // Sin lote asignado - puede ser reprocesado
                    desConError.add(de);
                    sinLote++;
                } else {
                    // Verificar si el lote está en estado final
                    LoteDE lote = de.getLoteDe();
                    EstadoLoteDE estadoLote = lote.getEstado();
                    
                    // Estados más comunes según experiencia: RECHAZADO y PROCESADO_CON_ERRORES
                    if (estadoLote == EstadoLoteDE.RECHAZADO) {
                        desConError.add(de);
                        conLoteRechazado++;
                    } else if (estadoLote == EstadoLoteDE.PROCESADO_CON_ERRORES) {
                        desConError.add(de);
                        conLoteProcesadoConErrores++;
                    } else if (estadoLote == EstadoLoteDE.PROCESADO || 
                               estadoLote == EstadoLoteDE.ERROR_PERMANENTE) {
                        // Otros estados finales (menos comunes pero válidos)
                        desConError.add(de);
                        conLoteOtroEstadoFinal++;
                    } else {
                        // Lote en estado activo - no se puede reprocesar aún
                        log.debug("   ⏭️  DE {} omitido - tiene lote {} en estado activo: {}", 
                            de.getId(), lote.getId(), estadoLote);
                        conLoteActivo++;
                    }
                }
            }
            
            // Mostrar estadísticas detalladas
            log.info("📊 Estadísticas de filtrado:");
            log.info("   📄 Sin lote asignado: {}", sinLote);
            log.info("   ❌ Con lote RECHAZADO: {}", conLoteRechazado);
            log.info("   ⚠️  Con lote PROCESADO_CON_ERRORES: {}", conLoteProcesadoConErrores);
            log.info("   📋 Con lote en otro estado final: {}", conLoteOtroEstadoFinal);
            log.info("   ⏳ Con lote activo (omitidos): {}", conLoteActivo);
            
            if (desConError.isEmpty()) {
                log.info("ℹ️  No hay DEs con el mensaje '{}' que puedan ser reprocesados (todos tienen lotes activos)", mensajeError);
                return;
            }
            
            log.info("✅ {} DEs pueden ser reprocesados (filtrados de {} totales)", desConError.size(), todosLosDesConError.size());
            
            // 2. Agrupar DEs en lotes (máximo según configuración, default 50)
            List<List<com.franco.dev.domain.financiero.DocumentoElectronico>> lotes = 
                dividirEnLotes(desConError, maxDocumentosPorLote);
            
            log.info("📦 Se crearán {} lotes nuevos", lotes.size());
            
            // 3. Procesar cada lote
            int lotesEnviados = 0;
            int lotesConError = 0;
            int lotesConsultados = 0;
            
            for (int i = 0; i < lotes.size(); i++) {
                List<com.franco.dev.domain.financiero.DocumentoElectronico> loteDEs = lotes.get(i);
                log.info("\n--- Procesando lote {} de {} ({} DEs) ---", i + 1, lotes.size(), loteDEs.size());
                
                LoteDE lote = null;
                try {
                    // 3.1. Crear lote en BD
                    lote = sifenService.crearLote();
                    log.info("✅ Lote creado con ID: {}", lote.getId());
                    
                    // 3.2. Resetear estado de los documentos, limpiar mensaje de error y referencia a lote anterior
                    for (com.franco.dev.domain.financiero.DocumentoElectronico de : loteDEs) {
                        de.setEstado(EstadoDE.PENDIENTE);
                        de.setMensajeRespuestaSifen(null);
                        de.setCodigoRespuestaSifen(null);
                        de.setLoteDe(null); // Limpiar referencia al lote anterior
                        documentoElectronicoService.save(de);
                    }
                    log.info("✅ Estados de {} DEs reseteados a PENDIENTE y limpiada referencia a lote anterior", loteDEs.size());
                    
                    // 3.3. Vincular DEs al lote
                    sifenService.vincularDocumentosALote(lote, loteDEs);
                    log.info("✅ {} DEs vinculados al lote", loteDEs.size());
                    
                    // 3.4. Enviar lote a SIFEN
                    sifenService.enviarLote(lote);
                    
                    // Verificar resultado del envío
                    LoteDE loteActualizado = loteDEService.findById(lote.getId()).orElse(null);
                    if (loteActualizado != null) {
                        if (loteActualizado.getEstado() == EstadoLoteDE.EN_PROCESO) {
                            log.info("✅ Lote {} enviado exitosamente - Protocolo: {}", 
                                lote.getId(), loteActualizado.getProtocolo());
                            lotesEnviados++;
                            
                            // 3.5. Esperar un momento y consultar el lote
                            log.info("   ⏳ Esperando 3 segundos antes de consultar el lote...");
                            Thread.sleep(3000);
                            
                            try {
                                sifenService.consultarLote(loteActualizado);
                                lotesConsultados++;
                                
                                // Verificar estado después de consulta
                                LoteDE loteDespuesConsulta = loteDEService.findById(lote.getId()).orElse(null);
                                if (loteDespuesConsulta != null) {
                                    log.info("✅ Lote {} consultado - Estado final: {}", 
                                        lote.getId(), loteDespuesConsulta.getEstado());
                                }
                            } catch (SifenException e) {
                                log.warn("⚠️  Error al consultar lote {} inmediatamente: {} - Se consultará en próxima ejecución", 
                                    lote.getId(), e.getMessage());
                            }
                        } else {
                            log.error("❌ Lote {} con error al enviar - Estado: {}", 
                                lote.getId(), loteActualizado.getEstado());
                            lotesConError++;
                        }
                    }
                    
                } catch (SifenException e) {
                    log.error("❌ Error de SIFEN al procesar lote {} de {}: {}", 
                        i + 1, lotes.size(), e.getMessage(), e);
                    // Asegurar que el lote se marque con error si existe
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
                } catch (InterruptedException e) {
                    log.error("❌ Procesamiento interrumpido durante espera", e);
                    Thread.currentThread().interrupt();
                    lotesConError++;
                } catch (Exception e) {
                    log.error("❌ Error inesperado al procesar lote {} de {}: {}", 
                        i + 1, lotes.size(), e.getMessage(), e);
                    // Asegurar que el lote se marque con error si existe
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
            log.info("\n📊 RESUMEN DE REPROCESAMIENTO DE DOCUMENTOS CON FECHA ADELANTADA:");
            log.info("   ✅ Lotes enviados exitosamente: {}", lotesEnviados);
            log.info("   🔍 Lotes consultados: {}", lotesConsultados);
            log.info("   ❌ Lotes con error: {}", lotesConError);
            log.info("   📋 Total de lotes procesados: {}", lotes.size());
            log.info("   📄 Total de documentos procesados: {}", desConError.size());
            log.info("=================================================================");
            
        } catch (Exception e) {
            log.error("❌ Error al reprocesar documentos con fecha adelantada", e);
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
