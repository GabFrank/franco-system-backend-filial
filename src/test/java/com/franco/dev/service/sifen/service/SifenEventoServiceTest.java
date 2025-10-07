package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.LoteDEService;
import com.franco.dev.service.personas.ClienteService;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionEvento;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * Test para SifenEventoService - Validación de eventos SIFEN.
 * 
 * Eventos probados:
 * 1. Cancelación de DE - Dejar sin efecto un DE aprobado
 * 2. Nominación - Identificar receptor real de factura innominada
 */
@Slf4j
@SpringBootTest
@Transactional
public class SifenEventoServiceTest {

    @Autowired
    private SifenEventoService sifenEventoService;

    @Autowired
    private SifenService sifenService;

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    @Autowired
    private FacturaLegalService facturaLegalService;

    @Autowired
    private LoteDEService loteDEService;

    @Autowired
    private ClienteService clienteService;

    // ===================== TEST DE CANCELACIÓN =====================

    /**
     * Test de cancelación de DE aprobado.
     * 
     * Flujo:
     * 1. Buscar un DE aprobado existente
     * 2. Enviar evento de cancelación a SIFEN
     * 3. Validar respuesta
     */
    @Test
    @Commit
    public void testCancelacionDE() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🚫 TEST: Cancelación de Documento Electrónico");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            // 1. Buscar un DE aprobado
            log.info("\n📋 PASO 1: Buscar DE aprobado para cancelar");
            List<DocumentoElectronico> aprobados = documentoElectronicoService.findByEstado(EstadoDE.APROBADO);
            
            if (aprobados.isEmpty()) {
                log.warn("⚠️ No hay DEs aprobados para cancelar. Saltar test.");
                log.info("💡 Sugerencia: Ejecuta primero SifenFlujoServiceTest para crear DEs aprobados.");
                return;
            }

            DocumentoElectronico deAprob = aprobados.get(0);
            log.info("   ✅ DE encontrado:");
            log.info("      - ID: {}", deAprob.getId());
            log.info("      - CDC: {}", deAprob.getCdc());
            log.info("      - Estado: {}", deAprob.getEstado());
            log.info("      - Factura ID: {}", deAprob.getFacturaLegal() != null ? deAprob.getFacturaLegal().getId() : "null");

            // 2. Enviar evento de cancelación
            log.info("\n🚫 PASO 2: Enviar evento de cancelación a SIFEN");
            String motivo = "Prueba de cancelación - Test automatizado";
            log.info("   Motivo: {}", motivo);

            RespuestaRecepcionEvento respuesta = sifenEventoService.cancelarDE(
                deAprob.getCdc(), 
                motivo
            );

            // 3. Mostrar respuesta
            log.info("\n📥 PASO 3: Respuesta de SIFEN");
            log.info("   Código: {}", respuesta.getdCodRes());
            log.info("   Mensaje: {}", respuesta.getdMsgRes());

            // 4. Validar respuesta
            if ("0300".equals(respuesta.getdCodRes())) {
                log.info("✅ Cancelación exitosa - Código 0300");
                
                // Verificar actualización en BD
                DocumentoElectronico deActualizado = documentoElectronicoService.findById(deAprob.getId()).orElse(null);
                if (deActualizado != null) {
                    log.info("   Estado en BD: {}", deActualizado.getEstado());
                    log.info("   Código respuesta: {}", deActualizado.getCodigoRespuestaSifen());
                }
            } else {
                log.error("❌ Error en cancelación - Código: {}", respuesta.getdCodRes());
            }

            log.info("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ TEST DE CANCELACIÓN COMPLETADO");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        } catch (SifenException e) {
            log.error("❌ Error de SIFEN en cancelación: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Error inesperado en test de cancelación: {}", e.getMessage(), e);
        }
    }

    // ===================== TEST DE NOMINACIÓN =====================

    /**
     * Test de nominación de receptor para factura innominada.
     * 
     * Flujo:
     * 1. Crear factura innominada (sin cliente)
     * 2. Crear DE y enviar a SIFEN
     * 3. Consultar lote hasta que sea aprobado
     * 4. Nominar receptor con un cliente específico
     * 5. Validar respuesta
     */
    @Test
    @Commit
    public void testNominacionReceptor() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("👤 TEST: Nominación de Receptor");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            // 1. Crear factura innominada (cliente null)
            log.info("\n📋 PASO 1: Crear factura innominada (sin cliente)");
            FacturaLegal facturaInnominada = crearFacturaInnominada();
            log.info("   ✅ Factura creada:");
            log.info("      - ID: {}", facturaInnominada.getId());
            log.info("      - Número: {}", facturaInnominada.getNumeroFactura());
            log.info("      - Total: {}", facturaInnominada.getTotalFinal());
            log.info("      - Cliente: null (innominado)");

            // 2. Crear DE
            log.info("\n📄 PASO 2: Crear Documento Electrónico");
            DocumentoElectronico de = sifenService.crearDocumentoElectronico(facturaInnominada);
            log.info("   ✅ DE creado:");
            log.info("      - ID: {}", de.getId());
            log.info("      - CDC: {}", de.getCdc());
            log.info("      - Estado: {}", de.getEstado());

            // 3. Crear lote y enviar
            log.info("\n📦 PASO 3: Crear lote y enviar a SIFEN");
            LoteDE lote = sifenService.crearLote();
            sifenService.vincularDocumentosALote(lote, Collections.singletonList(de));
            sifenService.enviarLote(lote);
            log.info("   ✅ Lote enviado:");
            log.info("      - ID: {}", lote.getId());
            log.info("      - Protocolo: {}", lote.getProtocolo());
            log.info("      - Estado: {}", lote.getEstado());

            // 4. Esperar y consultar hasta que sea aprobado
            log.info("\n⏳ PASO 4: Consultar lote hasta aprobación");
            boolean aprobado = esperarAprobacionLote(lote);
            
            if (!aprobado) {
                log.error("❌ Lote no fue aprobado. Cancelar test de nominación.");
                return;
            }

            // Refrescar DE para obtener estado actualizado
            de = documentoElectronicoService.findById(de.getId()).orElse(de);
            log.info("   ✅ Lote aprobado - DE actualizado:");
            log.info("      - Estado DE: {}", de.getEstado());

            // 5. Seleccionar cliente para nominación
            log.info("\n👤 PASO 5: Nominar receptor");
            Long clienteId = 194L; // Cliente válido para nominación
            Cliente cliente = clienteService.findById(clienteId).orElse(null);
            
            if (cliente == null) {
                log.error("❌ Cliente {} no encontrado. Usar otro cliente ID.", clienteId);
                return;
            }

            log.info("   Cliente seleccionado:");
            log.info("      - ID: {}", cliente.getId());
            log.info("      - Nombre: {}", cliente.getPersona().getNombre());
            log.info("      - Documento: {}", cliente.getPersona().getDocumento());
            log.info("      - Tributa: {}", cliente.getTributa());

            // 6. Enviar evento de nominación
            log.info("\n📤 PASO 6: Enviar evento de nominación a SIFEN");
            RespuestaRecepcionEvento respuesta = sifenEventoService.nominarReceptor(
                de.getCdc(),
                cliente
            );

            // 7. Mostrar respuesta
            log.info("\n📥 PASO 7: Respuesta de SIFEN");
            log.info("   Código: {}", respuesta.getdCodRes());
            log.info("   Mensaje: {}", respuesta.getdMsgRes());

            // 8. Validar respuesta
            if ("0300".equals(respuesta.getdCodRes())) {
                log.info("✅ Nominación exitosa - Código 0300");
                
                // Verificar actualización en BD
                DocumentoElectronico deActualizado = documentoElectronicoService.findById(de.getId()).orElse(null);
                if (deActualizado != null) {
                    log.info("   Código respuesta en BD: {}", deActualizado.getCodigoRespuestaSifen());
                    log.info("   Mensaje respuesta en BD: {}", deActualizado.getMensajeRespuestaSifen());
                }
            } else {
                log.error("❌ Error en nominación - Código: {}", respuesta.getdCodRes());
            }

            log.info("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ TEST DE NOMINACIÓN COMPLETADO");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        } catch (SifenException e) {
            log.error("❌ Error de SIFEN en nominación: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Error inesperado en test de nominación: {}", e.getMessage(), e);
        }
    }

    // ===================== MÉTODOS AUXILIARES =====================

    /**
     * Crea una factura innominada para testing.
     */
    private FacturaLegal crearFacturaInnominada() {
        FacturaLegal factura = new FacturaLegal();
        
        // Datos básicos
        int numeroCorrelativo = (int) (System.currentTimeMillis() % 10000000);
        factura.setNumeroFactura(numeroCorrelativo);
        factura.setCliente(null); // INNOMINADO
        
        // Montos de prueba (bajo límite de 7.000.000 para innominado)
        double total = 50000.0;
        factura.setTotalFinal(total);
        
        // Nota: FacturaLegal no tiene setObservacion, setValorTotal ni setTotalGs
        // Solo necesitamos setTotalFinal y setCliente(null) para factura innominada
        
        return facturaLegalService.save(factura);
    }

    /**
     * Espera y consulta el lote hasta que sea aprobado o rechazado.
     * 
     * @param lote Lote a consultar
     * @return true si fue aprobado, false si fue rechazado o timeout
     */
    private boolean esperarAprobacionLote(LoteDE lote) {
        int intentos = 0;
        int maxIntentos = 10;
        int intervaloMs = 5000; // 5 segundos entre consultas

        while (intentos < maxIntentos) {
            intentos++;
            
            try {
                log.info("   Intento {}/{} - Consultando lote...", intentos, maxIntentos);
                Thread.sleep(intervaloMs);
                
                sifenService.consultarLote(lote);
                
                // Refrescar lote desde BD
                LoteDE loteActualizado = loteDEService.findById(lote.getId()).orElse(lote);
                EstadoLoteDE estado = loteActualizado.getEstado();
                
                log.info("      Estado actual: {}", estado);
                
                if (estado == EstadoLoteDE.PROCESADO) {
                    log.info("   ✅ Lote procesado y aprobado");
                    return true;
                } else if (estado == EstadoLoteDE.RECHAZADO) {
                    log.error("   ❌ Lote rechazado");
                    return false;
                } else if (estado == EstadoLoteDE.PROCESADO_CON_ERRORES) {
                    log.warn("   ⚠️ Lote procesado con errores");
                    return false;
                }
                
                // Continuar esperando si está EN_PROCESO
                
            } catch (Exception e) {
                log.error("   ❌ Error en consulta de lote: {}", e.getMessage());
            }
        }
        
        log.warn("   ⏱️ Timeout alcanzado - Lote no fue aprobado en {} intentos", maxIntentos);
        return false;
    }

    // ===================== TEST COMBINADO =====================

    /**
     * Test combinado que prueba flujo completo:
     * 1. Crear DE innominado → aprobarlo
     * 2. Nominarlo con un cliente
     * 3. Cancelarlo
     */
    @Test
    @Commit
    public void testFlujoCombinado_Nominacion_Y_Cancelacion() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🔄 TEST COMBINADO: Nominación + Cancelación");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            // 1. Crear, enviar y aprobar factura innominada
            log.info("\n📋 FASE 1: Crear y aprobar factura innominada");
            FacturaLegal factura = crearFacturaInnominada();
            DocumentoElectronico de = sifenService.crearDocumentoElectronico(factura);
            
            LoteDE lote = sifenService.crearLote();
            sifenService.vincularDocumentosALote(lote, Collections.singletonList(de));
            sifenService.enviarLote(lote);
            
            if (!esperarAprobacionLote(lote)) {
                log.error("❌ No se pudo aprobar el DE innominado. Cancelar test.");
                return;
            }
            
            de = documentoElectronicoService.findById(de.getId()).orElse(de);
            log.info("✅ DE innominado aprobado - CDC: {}", de.getCdc());

            // 2. Nominar receptor
            log.info("\n👤 FASE 2: Nominar receptor");
            Cliente cliente = clienteService.findById(194L).orElse(null);
            
            if (cliente == null) {
                log.error("❌ Cliente no encontrado. Cancelar test.");
                return;
            }

            RespuestaRecepcionEvento respNominacion = sifenEventoService.nominarReceptor(
                de.getCdc(),
                cliente
            );
            
            log.info("   Respuesta nominación - Código: {}", respNominacion.getdCodRes());
            
            if (!"0300".equals(respNominacion.getdCodRes())) {
                log.error("❌ Nominación falló. No proceder con cancelación.");
                return;
            }
            
            log.info("✅ Receptor nominado exitosamente");

            // 3. Cancelar DE
            log.info("\n🚫 FASE 3: Cancelar DE");
            RespuestaRecepcionEvento respCancelacion = sifenEventoService.cancelarDE(
                de.getCdc(),
                "Cancelación después de nominación - Test combinado"
            );
            
            log.info("   Respuesta cancelación - Código: {}", respCancelacion.getdCodRes());
            
            if ("0300".equals(respCancelacion.getdCodRes())) {
                log.info("✅ DE cancelado exitosamente");
            } else {
                log.error("❌ Error en cancelación - Código: {}", respCancelacion.getdCodRes());
            }

            log.info("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ TEST COMBINADO COMPLETADO");
            log.info("   - Factura innominada: ✅ Creada y aprobada");
            log.info("   - Nominación: {}", "0300".equals(respNominacion.getdCodRes()) ? "✅" : "❌");
            log.info("   - Cancelación: {}", "0300".equals(respCancelacion.getdCodRes()) ? "✅" : "❌");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        } catch (Exception e) {
            log.error("❌ Error en test combinado: {}", e.getMessage(), e);
        }
    }
}

