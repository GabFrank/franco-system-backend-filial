package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.LoteDE;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@EnableScheduling
public class SifenSchedulerService {

    @Value("${sifen.scheduler.enabled:true}")
    private Boolean schedulerEnabled;

    private final SifenService sifenService;

    public SifenSchedulerService(SifenService sifenService) {
        this.sifenService = sifenService;
    }

    /**
     * Método principal del scheduler que ejecuta el ciclo completo de procesamiento SIFEN.
     * Se ejecuta según el intervalo configurado en application.properties.
     */
    @Scheduled(fixedDelayString = "${sifen.scheduler.fixed-delay:300000}")
    public void procesarCicloSifen() {
        if (!schedulerEnabled) {
            log.debug("Scheduler SIFEN deshabilitado. Saltando ciclo de procesamiento.");
            return;
        }

        log.info("=== Iniciando ciclo de procesamiento SIFEN ===");
        long inicioCiclo = System.currentTimeMillis();

        try {
            // Paso 1: Crear lotes con documentos pendientes
            log.info("Paso 1: Creando lotes con documentos pendientes...");
            sifenService.crearLotesConDocumentosPendientes();

            // Paso 2: Enviar lotes pendientes
            log.info("Paso 2: Enviando lotes pendientes...");
            procesarLotesPendientes();

            // Paso 3: Consultar resultados de lotes en proceso
            log.info("Paso 3: Consultando resultados de lotes en proceso...");
            procesarLotesEnProceso();

            // Paso 4: Reintentar lotes con errores
            log.info("Paso 4: Reintentando lotes con errores...");
            procesarLotesConErrores();

        } catch (Exception e) {
            log.error("Error durante el ciclo de procesamiento SIFEN: {}", e.getMessage(), e);
        } finally {
            long duracionCiclo = System.currentTimeMillis() - inicioCiclo;
            log.info("=== Ciclo de procesamiento SIFEN finalizado en {} ms ===", duracionCiclo);
        }
    }

    /**
     * Procesa lotes que están listos para ser enviados.
     */
    private void procesarLotesPendientes() {
        List<LoteDE> lotesPendientes = sifenService.obtenerLotesParaEnvio();
        
        if (lotesPendientes.isEmpty()) {
            log.debug("No hay lotes pendientes para enviar.");
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
    }

    /**
     * Procesa lotes que están en proceso y necesitan consulta de resultado.
     */
    private void procesarLotesEnProceso() {
        List<LoteDE> lotesEnProceso = sifenService.obtenerLotesEnProceso();
        
        if (lotesEnProceso.isEmpty()) {
            log.debug("No hay lotes en proceso para consultar.");
            return;
        }

        log.info("Encontrados {} lotes en proceso para consultar.", lotesEnProceso.size());

        for (LoteDE lote : lotesEnProceso) {
            try {
                // Solo consultar lotes que han estado en proceso por más de 5 minutos
                if (lote.getFechaUltimoIntento() != null && 
                    lote.getFechaUltimoIntento().isBefore(LocalDateTime.now().minusMinutes(5))) {
                    
                    log.info("Consultando resultado del lote {}...", lote.getId());
                    sifenService.consultarResultadoLote(lote);
                    
                    // Pequeña pausa entre consultas
                    Thread.sleep(1000);
                }
                
            } catch (Exception e) {
                log.error("Error al consultar resultado del lote {}: {}", lote.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Procesa lotes que fallaron y pueden ser reintentados.
     * Distingue entre errores de red (que requieren espera) y errores de SIFEN (que pueden reintentarse).
     */
    private void procesarLotesConErrores() {
        List<LoteDE> lotesConErrores = sifenService.obtenerLotesParaReintento();
        
        if (lotesConErrores.isEmpty()) {
            log.debug("No hay lotes con errores para reintentar.");
            return;
        }

        log.info("Encontrados {} lotes con errores para reintentar.", lotesConErrores.size());

        for (LoteDE lote : lotesConErrores) {
            try {
                // Solo reintentar si ha pasado el tiempo suficiente según el tipo de error
                if (debeReintentarLote(lote)) {
                    log.info("Reintentando envío del lote {} (intento {})...", 
                            lote.getId(), lote.getIntentos() + 1);
                    sifenService.enviarLote(lote);
                    
                    // Pausa más larga para reintentos
                    Thread.sleep(5000);
                } else {
                    log.debug("Lote {} aún no debe ser reintentado según el backoff.", lote.getId());
                }
                
            } catch (Exception e) {
                log.error("Error al reintentar lote {}: {}", lote.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Determina si un lote debe ser reintentado basado en backoff exponencial.
     * Considera el tipo de error para aplicar diferentes estrategias de reintento.
     */
    private boolean debeReintentarLote(LoteDE lote) {
        if (lote.getFechaUltimoIntento() == null) {
            return true;
        }

        // Diferentes estrategias según el estado del lote
        switch (lote.getEstado()) {
            case ERROR_RED:
                // Para errores de red, esperar más tiempo (30 minutos mínimo)
                LocalDateTime proximoIntentoRed = lote.getFechaUltimoIntento().plusMinutes(30);
                return LocalDateTime.now().isAfter(proximoIntentoRed);
                
            case ERROR_ENVIO:
                // Para errores de envío, usar backoff exponencial: 1 min, 2 min, 4 min, 8 min, 16 min
                int minutosEspera = (int) Math.pow(2, lote.getIntentos());
                LocalDateTime proximoIntento = lote.getFechaUltimoIntento().plusMinutes(minutosEspera);
                return LocalDateTime.now().isAfter(proximoIntento);
                
            default:
                return false; // No reintentar otros estados
        }
    }

    /**
     * Método para ejecutar manualmente el ciclo de procesamiento (útil para testing o administración).
     */
    public void ejecutarCicloManual() {
        log.info("Ejecutando ciclo manual de procesamiento SIFEN...");
        procesarCicloSifen();
    }

    /**
     * Obtiene estadísticas del sistema de lotes.
     */
    public String obtenerEstadisticas() {
        try {
            int lotesPendientes = sifenService.obtenerLotesParaEnvio().size();
            int lotesEnProceso = sifenService.obtenerLotesEnProceso().size();
            int lotesConErrores = sifenService.obtenerLotesParaReintento().size();
            
            return String.format(
                "Estadísticas SIFEN - Pendientes: %d, En Proceso: %d, Con Errores: %d",
                lotesPendientes, lotesEnProceso, lotesConErrores
            );
            
        } catch (Exception e) {
            log.error("Error al obtener estadísticas: {}", e.getMessage(), e);
            return "Error al obtener estadísticas";
        }
    }
}
