package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.roshka.sifen.core.beans.response.RespuestaConsultaDE;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Test para consulta y actualización de Documentos Electrónicos.
 * 
 * Funcionalidades:
 * 1. Consultar un único DE por CDC
 * 2. Consultar múltiples DEs filtrados por estado
 * 3. Actualizar estado en base de datos según respuesta SIFEN
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
@Transactional
public class SifenDETest {

    @Autowired
    private SifenService sifenService;

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    // ===================== TEST DE CONSULTA POR CDC =====================

    /**
     * Test de consulta de un único Documento Electrónico por CDC.
     * 
     * Flujo:
     * 1. Consultar DE en SIFEN usando CDC específico
     * 2. El método consultarDE automáticamente actualiza el estado en BD
     * 3. Verificar actualización en base de datos
     * 4. Mostrar información del DE y sus eventos asociados
     */
    @Test
    @Commit
    public void testConsultarDEPorCDC() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🔍 TEST: Consulta de Documento Electrónico por CDC");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            // CDC de prueba - Reemplazar con un CDC válido de tu sistema
            String cdcPrueba = "01800994825001001000003522025100110462644638";
            
            log.info("\n📄 PASO 1: Buscar DE en base de datos local");
            Optional<DocumentoElectronico> deOptional = documentoElectronicoService.findByCdc(cdcPrueba);
            
            if (!deOptional.isPresent()) {
                log.warn("⚠️ DE con CDC {} no encontrado en base de datos local", cdcPrueba);
                log.info("💡 Sugerencia: Verifica que el CDC sea correcto o ejecuta primero tests de creación de DEs");
                return;
            }
            
            DocumentoElectronico deAntes = deOptional.get();
            log.info("   ✅ DE encontrado:");
            log.info("      - ID: {}", deAntes.getId());
            log.info("      - CDC: {}", deAntes.getCdc());
            log.info("      - Estado antes: {}", deAntes.getEstado());
            log.info("      - Fecha emisión: {}", deAntes.getFechaEmision());

            log.info("\n📡 PASO 2: Consultar DE en SIFEN");
            log.info("   CDC: {}", cdcPrueba);

            // Consultar DE - Este método actualiza automáticamente el estado en BD
            RespuestaConsultaDE respuesta = sifenService.consultarDE(cdcPrueba);

            log.info("\n📥 PASO 3: Respuesta de SIFEN");
            log.info("   Código: {}", respuesta.getdCodRes());
            log.info("   Mensaje: {}", respuesta.getdMsgRes());

            // Verificar actualización en BD
            log.info("\n💾 PASO 4: Verificar actualización en base de datos");
            DocumentoElectronico deDespues = documentoElectronicoService.findById(deAntes.getId())
                .orElse(null);
            
            if (deDespues != null) {
                log.info("   ✅ DE actualizado:");
                log.info("      - ID: {}", deDespues.getId());
                log.info("      - CDC: {}", deDespues.getCdc());
                log.info("      - Estado después: {}", deDespues.getEstado());
                log.info("      - Código respuesta: {}", deDespues.getCodigoRespuestaSifen());
                log.info("      - Mensaje respuesta: {}", deDespues.getMensajeRespuestaSifen());
                log.info("      - Fecha recepción SIFEN: {}", deDespues.getFechaRecepcionSifen());
                
                // Validar cambio de estado
                if (deAntes.getEstado() != deDespues.getEstado()) {
                    log.info("   🔄 Estado cambió: {} → {}", deAntes.getEstado(), deDespues.getEstado());
                } else {
                    log.info("   ℹ️ Estado sin cambios: {}", deDespues.getEstado());
                }
            }

            log.info("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ TEST DE CONSULTA POR CDC COMPLETADO");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        } catch (Exception e) {
            log.error("❌ Error al consultar DE: {}", e.getMessage(), e);
        }
    }

    // ===================== TEST DE CONSULTA MÚLTIPLE POR ESTADO =====================

    /**
     * Test de consulta de múltiples Documentos Electrónicos filtrados por estado.
     * 
     * Flujo:
     * 1. Buscar todos los DEs con un estado específico (o todos si estado es null)
     * 2. Consultar cada DE individualmente en SIFEN
     * 3. Actualizar estados automáticamente
     * 4. Mostrar resumen de actualizaciones
     * 
     * @param estado Estado para filtrar (null = traer todos)
     */
    @Test
    @Commit
    public void testConsultarDEsPorEstado_APROBADO() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🔍 TEST: Consulta de DEs filtrados por estado APROBADO");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        consultarDEsPorEstado(EstadoDE.APROBADO);
    }

    @Test
    @Commit
    public void testConsultarDEsPorEstado_EN_LOTE() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🔍 TEST: Consulta de DEs filtrados por estado EN_LOTE");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        consultarDEsPorEstado(EstadoDE.EN_LOTE);
    }

    /**
     * Método común para consultar DEs por estado.
     */
    private void consultarDEsPorEstado(EstadoDE estado) {
        try {
            // 1. Buscar DEs según filtro
            log.info("\n📋 PASO 1: Buscar DEs en base de datos local");
            log.info("   Filtro: Estado = {}", estado);
            
            List<DocumentoElectronico> documentos = documentoElectronicoService.findByEstado(estado);
            
            if (documentos.isEmpty()) {
                log.warn("⚠️ No se encontraron DEs con estado {}", estado);
                log.info("💡 Sugerencia: Ejecuta primero tests de creación de DEs o prueba con otro estado");
                return;
            }
            
            log.info("   ✅ {} documento(s) encontrado(s)", documentos.size());

            // 2. Limitar cantidad para evitar muchas consultas en pruebas
            int maxConsultas = 5;
            int totalConsultar = Math.min(documentos.size(), maxConsultas);
            
            if (documentos.size() > maxConsultas) {
                log.info("   ℹ️ Limitando a {} consultas (de {} disponibles)", maxConsultas, documentos.size());
                documentos = documentos.subList(0, maxConsultas);
            }

            // 3. Consultar cada DE individualmente
            log.info("\n📡 PASO 2: Consultar cada DE en SIFEN");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            int exitosos = 0;
            int errores = 0;
            int estadosActualizados = 0;
            
            for (int i = 0; i < documentos.size(); i++) {
                DocumentoElectronico de = documentos.get(i);
                
                log.info("\n   [{}/{}] Consultando DE:", (i + 1), totalConsultar);
                log.info("      - ID: {}", de.getId());
                log.info("      - CDC: {}", de.getCdc());
                log.info("      - Estado actual: {}", de.getEstado());
                
                EstadoDE estadoAntes = de.getEstado();
                
                try {
                    // Consultar DE - actualiza automáticamente el estado en BD
                    RespuestaConsultaDE respuesta = sifenService.consultarDE(de.getCdc());
                    
                    log.info("      📥 Respuesta: {} - {}", 
                        respuesta.getdCodRes(), 
                        respuesta.getdMsgRes());
                    
                    // Refrescar DE desde BD para ver cambios
                    DocumentoElectronico deActualizado = documentoElectronicoService.findById(de.getId())
                        .orElse(de);
                    
                    if (estadoAntes != deActualizado.getEstado()) {
                        log.info("      🔄 Estado actualizado: {} → {}", 
                            estadoAntes, 
                            deActualizado.getEstado());
                        estadosActualizados++;
                    } else {
                        log.info("      ✓ Estado sin cambios: {}", deActualizado.getEstado());
                    }
                    
                    exitosos++;
                    
                } catch (Exception e) {
                    log.error("      ❌ Error: {}", e.getMessage());
                    errores++;
                }
            }

            // 4. Resumen de ejecución
            log.info("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("📊 RESUMEN DE CONSULTAS");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("   Total DEs consultados: {}", totalConsultar);
            log.info("   ✅ Exitosos: {}", exitosos);
            log.info("   ❌ Con errores: {}", errores);
            log.info("   🔄 Estados actualizados: {}", estadosActualizados);
            log.info("   ℹ️ Sin cambios: {}", (exitosos - estadosActualizados));
            
            log.info("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ TEST DE CONSULTA MÚLTIPLE COMPLETADO");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        } catch (Exception e) {
            log.error("❌ Error en test de consulta múltiple: {}", e.getMessage(), e);
        }
    }

    // ===================== TEST DE CONSULTA CON LIMITE PERSONALIZADO =====================

    /**
     * Test de consulta con límite personalizado de DEs.
     * Útil cuando hay muchos documentos y quieres controlar cuántos consultar.
     */
    @Test
    @Commit
    public void testConsultarDEsConLimite() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🔍 TEST: Consulta limitada de DEs");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            EstadoDE estadoFiltro = EstadoDE.APROBADO;
            int limite = 10; // Consultar 10 DEs
            
            log.info("\n📋 Configuración:");
            log.info("   Estado filtro: {}", estadoFiltro);
            log.info("   Límite: {}", limite);

            List<DocumentoElectronico> documentos = documentoElectronicoService.findAllWithLimit(10);
            
            if (documentos.isEmpty()) {
                log.warn("⚠️ No hay DEs con estado {}", estadoFiltro);
                return;
            }
            
            log.info("   Total disponibles: {}", documentos.size());
            
            // Limitar cantidad
            int totalConsultar = Math.min(documentos.size(), limite);
            documentos = documentos.subList(0, totalConsultar);
            
            log.info("   Total a consultar: {}", totalConsultar);

            // Consultar
            for (int i = 0; i < documentos.size(); i++) {
                DocumentoElectronico de = documentos.get(i);
                
                log.info("\n   [{}/{}] Consultando CDC: {}", 
                    (i + 1), totalConsultar, de.getCdc());
                
                try {
                    RespuestaConsultaDE respuesta = sifenService.consultarDE(de.getCdc());
                    log.info("      ✅ {}: {}", 
                        respuesta.getdCodRes(), 
                        respuesta.getdMsgRes());
                    
                } catch (Exception e) {
                    log.error("      ❌ Error: {}", e.getMessage());
                }
            }

            log.info("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ TEST DE CONSULTA CON LÍMITE COMPLETADO");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        } catch (Exception e) {
            log.error("❌ Error en test de consulta con límite: {}", e.getMessage(), e);
        }
    }
}

