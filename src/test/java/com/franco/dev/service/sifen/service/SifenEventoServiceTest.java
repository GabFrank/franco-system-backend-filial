package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.personas.ClienteService;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionEvento;
import com.roshka.sifen.core.exceptions.SifenException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Test para SifenEventoService - Validación de eventos SIFEN.
 * 
 * Eventos probados:
 * 1. Cancelación de DE - Dejar sin efecto un DE aprobado
 * 2. Nominación - Identificar receptor real de factura innominada
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
@Transactional
public class SifenEventoServiceTest {

    @Autowired
    private SifenEventoService sifenEventoService;

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

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
            // CDC de prueba - Reemplazar con un CDC válido de un DE aprobado
            String cdc = "01800994825001001000005322025100614653130125";
            String motivo = "Prueba de cancelación - Test automatizado";
            
            log.info("\n📋 PASO 1: Configuración");
            log.info("   CDC: {}", cdc);
            log.info("   Motivo: {}", motivo);

            // Verificar que el DE existe
            List<DocumentoElectronico> aprobados = documentoElectronicoService.findByEstado(EstadoDE.APROBADO);
            
            if (aprobados.isEmpty()) {
                log.warn("⚠️ No hay DEs aprobados para cancelar. Saltar test.");
                log.info("💡 Sugerencia: Ejecuta primero SifenFlujoServiceTest para crear DEs aprobados.");
                return;
            }

            log.info("\n📤 PASO 2: Enviar evento de cancelación a SIFEN");
            RespuestaRecepcionEvento respuesta = sifenEventoService.cancelarDE(cdc, motivo);

            // 3. Mostrar respuesta
            log.info("\n📥 PASO 3: Respuesta de SIFEN");
            log.info("   Código: {}", respuesta.getRespuestaBruta());

            // 4. Validar respuesta
            if ("0300".equals(respuesta.getdCodRes())) {
                log.info("✅ Cancelación exitosa - Código 0300");
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
     * 1. Buscar un DE aprobado por CDC
     * 2. Buscar el cliente por ID
     * 3. Nominar receptor con el cliente especificado
     * 4. Validar respuesta
     * 
     * NOTA: Este test requiere:
     * - Un CDC de un DE aprobado e innominado (sin cliente)
     * - Un ID de cliente válido en la base de datos
     */
    @Test
    @Commit
    public void testNominacionReceptor() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("👤 TEST: Nominación de Receptor");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            // PASO 1: Configurar CDC y Cliente ID
            String cdc = "01800994825001001000006422025101310266529145"; // Reemplazar con un CDC válido
            Long clienteId = 2L; // Reemplazar con un ID de cliente válido
            
            log.info("\n📋 PASO 1: Configuración");
            log.info("   CDC: {}", cdc);
            log.info("   Cliente ID: {}", clienteId);
            
            // PASO 2: Buscar el Documento Electrónico
            log.info("\n📄 PASO 2: Buscar Documento Electrónico");
            DocumentoElectronico de = documentoElectronicoService.findByCdc(cdc).orElse(null);
            
            if (de == null) {
                log.error("❌ No se encontró DE con CDC: {}", cdc);
                log.info("💡 Sugerencia: Verifica que el CDC sea correcto");
                return;
            }
            
            log.info("   ✅ DE encontrado:");
            log.info("      - ID: {}", de.getId());
            log.info("      - CDC: {}", de.getCdc());
            log.info("      - Estado: {}", de.getEstado());
            log.info("      - Factura ID: {}", de.getFacturaLegal() != null ? de.getFacturaLegal().getId() : "null");
            
            // Validar que el DE esté aprobado
            if (de.getEstado() != EstadoDE.APROBADO) {
                log.warn("⚠️ El DE no está en estado APROBADO (estado actual: {})", de.getEstado());
                log.info("💡 La nominación solo aplica para DEs aprobados");
                return;
            }
            
            // PASO 3: Buscar el Cliente
            log.info("\n👤 PASO 3: Buscar Cliente");
            Cliente cliente = clienteService.findById(clienteId).orElse(null);
            
            if (cliente == null) {
                log.error("❌ No se encontró cliente con ID: {}", clienteId);
                log.info("💡 Sugerencia: Verifica que el ID del cliente sea correcto");
                return;
            }
            
            log.info("   ✅ Cliente encontrado:");
            log.info("      - ID: {}", cliente.getId());
            log.info("      - Nombre: {}", cliente.getPersona() != null ? cliente.getPersona().getNombre() : "null");
            log.info("      - Documento: {}", cliente.getPersona() != null ? cliente.getPersona().getDocumento() : "null");
            log.info("      - Tributa: {}", cliente.getTributa());
            
            // PASO 4: Nominar receptor
            log.info("\n📤 PASO 4: Nominar receptor en SIFEN");
            RespuestaRecepcionEvento respuesta = sifenEventoService.nominarReceptor(cdc, cliente);
            
            // PASO 5: Mostrar respuesta
            log.info("\n📥 PASO 5: Respuesta de SIFEN");
            log.info("   Código: {}", respuesta.getdCodRes());
            log.info("   Mensaje: {}", respuesta.getdMsgRes());
            
            // PASO 6: Validar respuesta
            log.info("\n✓ PASO 6: Validar respuesta");
            if ("0300".equals(respuesta.getdCodRes())) {
                log.info("   ✅ Nominación exitosa - Código 0300");
                
                // Verificar actualización en BD
                DocumentoElectronico deActualizado = documentoElectronicoService.findById(de.getId()).orElse(null);
                if (deActualizado != null) {
                    log.info("   📊 Estado del DE en BD:");
                    log.info("      - Código respuesta: {}", deActualizado.getCodigoRespuestaSifen());
                    log.info("      - Mensaje respuesta: {}", deActualizado.getMensajeRespuestaSifen());
                }
            } else if ("0600".equals(respuesta.getdCodRes())) {
                log.info("   ✅ Evento registrado - Código 0600 (pendiente de procesamiento)");
                log.info("   💡 El evento fue recibido y está en cola para procesamiento");
            } else {
                log.error("   ❌ Error en nominación - Código: {}", respuesta.getdCodRes());
                log.error("   📝 Mensaje: {}", respuesta.getdMsgRes());
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
}

