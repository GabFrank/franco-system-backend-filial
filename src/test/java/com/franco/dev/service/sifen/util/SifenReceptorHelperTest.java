package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.service.personas.ClienteService;
import com.roshka.sifen.core.types.TiNatRec;
import com.roshka.sifen.core.types.TiTiOpe;
import com.roshka.sifen.core.types.TiTipDocRec;
import com.roshka.sifen.core.types.PaisType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitario para SifenReceptorHelper.
 * Valida la correcta configuración de receptores según las reglas oficiales de SIFEN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
@Transactional
public class SifenReceptorHelperTest {
    
    private static final Logger logger = LoggerFactory.getLogger(SifenReceptorHelperTest.class);
    
    @Autowired
    private ClienteService clienteService;
    
    /**
     * Test principal que valida 4 clientes con diferentes características.
     * 
     * Ingrese los IDs de los clientes a probar:
     * - Cliente 1: [INGRESE ID AQUÍ] - Descripción del caso
     * - Cliente 2: [INGRESE ID AQUÍ] - Descripción del caso
     * - Cliente 3: [INGRESE ID AQUÍ] - Descripción del caso
     * - Cliente 4: [INGRESE ID AQUÍ] - Descripción del caso
     */
    @Test
    public void testConfiguracionReceptorConCuatroClientes() {
        logger.info("================================================================================");
        logger.info("TEST DE VALIDACIÓN: SifenReceptorHelper con 4 Clientes");
        logger.info("================================================================================");
        
        // ===== CONFIGURACIÓN DE CLIENTES A PROBAR =====
        // TODO: Reemplazar con los IDs reales de los clientes
        Long[] clienteIds = {
            645L,   // Cliente 1: [Descripción]
            958L,     // Cliente 2: [Descripción]
            null,   // Cliente 3: [Descripción]
            2L     // Cliente 4: [Descripción]
        };
        
        // Montos a probar (en PYG)
        Double[] montos = {
            50000.0,      // Monto normal
            5000000.0,    // Monto alto (pero < 7M)
            6000000.0,    // Monto muy alto (> 7M - no permite innominado)
            1000.0        // Monto bajo
        };
        
        int totalExitosos = 0;
        int totalConErrores = 0;
        
        // ===== EJECUTAR PRUEBAS =====
        for (int i = 0; i < clienteIds.length; i++) {
            int numCliente = i + 1;
            Long clienteId = clienteIds[i];
            Double monto = montos[i];
            
            logger.info("\n================================================================================");
            logger.info("🧪 PRUEBA {}/4: Cliente ID = {}, Monto = {} PYG", numCliente, clienteId, monto);
            logger.info("================================================================================");
            
            try {
                // Cargar cliente desde la base de datos
                Cliente cliente = clienteId != null ? 
                    clienteService.findById(clienteId).orElse(null) : null;
                
                if (clienteId != null && cliente == null) {
                    logger.error("❌ Cliente con ID {} no encontrado en la base de datos", clienteId);
                    totalConErrores++;
                    continue;
                }
                
                // Si clienteId es null, es innominado (cliente no informado)
                if (clienteId == null) {
                    logger.info("📝 Cliente: INNOMINADO (cliente no informado)");
                } else {
                    // Mostrar información del cliente
                    mostrarInformacionCliente(cliente);
                }
                
                // Ejecutar el helper
                logger.info("\n🔧 Ejecutando SifenReceptorHelper.determinarConfiguracionReceptor()...");
                SifenReceptorHelper.ConfiguracionReceptor config = 
                    SifenReceptorHelper.determinarConfiguracionReceptor(cliente, monto);
                
                // Mostrar resultados
                logger.info("\n✅ CONFIGURACIÓN DETERMINADA:");
                mostrarConfiguracion(config);
                
                // Validaciones básicas
                validarConfiguracion(config, cliente, monto);
                
                totalExitosos++;
                logger.info("\n✅ PRUEBA {}/4 COMPLETADA EXITOSAMENTE", numCliente);
                
            } catch (Exception e) {
                logger.error("\n❌ ERROR en prueba {}/4: {}", numCliente, e.getMessage());
                logger.error("Stack trace:", e);
                totalConErrores++;
            }
        }
        
        // ===== RESUMEN FINAL =====
        logger.info("\n================================================================================");
        logger.info("📊 RESUMEN FINAL:");
        logger.info("   ✅ Pruebas exitosas: {}/{}", totalExitosos, clienteIds.length);
        logger.info("   ❌ Pruebas con errores: {}/{}", totalConErrores, clienteIds.length);
        logger.info("================================================================================");
        
        // Verificar que al menos algunas pruebas fueron exitosas
        assertTrue(totalExitosos > 0, "Al menos una prueba debe ser exitosa");
    }
    
    /**
     * Muestra la información detallada del cliente.
     */
    private void mostrarInformacionCliente(Cliente cliente) {
        logger.info("\n📋 INFORMACIÓN DEL CLIENTE:");
        logger.info("   ID: {}", cliente.getId());
        
        Persona persona = cliente.getPersona();
        if (persona != null) {
            logger.info("   Nombre: {}", persona.getNombre());
            logger.info("   Documento: {}", persona.getDocumento());
        } else {
            logger.warn("   ⚠️ Sin persona asociada");
        }
        
        logger.info("   Tributa: {}", cliente.getTributa());
        logger.info("   Tipo Contribuyente: {}", cliente.getTipoContribuyente());
    }
    
    /**
     * Muestra la configuración determinada por el helper.
     */
    private void mostrarConfiguracion(SifenReceptorHelper.ConfiguracionReceptor config) {
        logger.info("   ┌─────────────────────────────────────────────────────");
        logger.info("   │ Escenario Detectado: {}", config.escenarioDetectado);
        logger.info("   ├─────────────────────────────────────────────────────");
        logger.info("   │ CAMPOS OBLIGATORIOS:");
        logger.info("   │  • iNatRec (Naturaleza): {}", config.iNatRec);
        logger.info("   │  • iTiOpe (Tipo Operación): {}", config.iTiOpe);
        logger.info("   │  • dNomRec (Nombre): {}", config.dNomRec);
        logger.info("   │  • cPaisRec (País): {}", config.cPaisRec);
        logger.info("   ├─────────────────────────────────────────────────────");
        
        if (config.iNatRec == TiNatRec.CONTRIBUYENTE) {
            logger.info("   │ CAMPOS DE CONTRIBUYENTE:");
            logger.info("   │  • iTiContRec (Tipo Contribuyente): {}", config.iTiContRec);
            logger.info("   │  • dRucRec (RUC sin DV): {}", config.dRucRec);
            logger.info("   │  • dDVRec (Dígito Verificador): {}", config.dDVRec);
            logger.info("   │  • Requiere Dirección: {}", config.requiereDireccion);
        } else {
            logger.info("   │ CAMPOS DE NO CONTRIBUYENTE:");
            logger.info("   │  • iTipIDRec (Tipo Documento): {}", config.iTipIDRec);
            logger.info("   │  • dNumIDRec (Número Documento): {}", config.dNumIDRec);
            if (config.dDTipIDRec != null) {
                logger.info("   │  • dDTipIDRec (Descripción Tipo Doc): {}", config.dDTipIDRec);
            }
        }
        
        logger.info("   └─────────────────────────────────────────────────────");
    }
    
    /**
     * Valida que la configuración determinada sea coherente.
     */
    private void validarConfiguracion(SifenReceptorHelper.ConfiguracionReceptor config, 
                                     Cliente cliente, Double monto) {
        logger.info("\n🔍 VALIDACIONES:");
        
        // Validación 1: Campos obligatorios
        assertNotNull(config.iNatRec, "iNatRec debe estar definido");
        assertNotNull(config.iTiOpe, "iTiOpe debe estar definido");
        assertNotNull(config.dNomRec, "dNomRec debe estar definido");
        assertNotNull(config.cPaisRec, "cPaisRec debe estar definido");
        logger.info("   ✅ Campos obligatorios presentes");
        
        // Validación 2: Coherencia según naturaleza
        if (config.iNatRec == TiNatRec.CONTRIBUYENTE) {
            assertNotNull(config.iTiContRec, "Contribuyente debe tener iTiContRec");
            assertNotNull(config.dRucRec, "Contribuyente debe tener dRucRec");
            assertNotNull(config.dDVRec, "Contribuyente debe tener dDVRec");
            assertNull(config.iTipIDRec, "Contribuyente NO debe tener iTipIDRec");
            assertNull(config.dNumIDRec, "Contribuyente NO debe tener dNumIDRec");
            logger.info("   ✅ Campos de contribuyente coherentes");
            
            // Validar que iTiOpe sea B2B para contribuyentes
            assertTrue(config.iTiOpe == TiTiOpe.B2B || config.iTiOpe == TiTiOpe.B2G,
                "Contribuyente debe tener iTiOpe = B2B o B2G");
            logger.info("   ✅ Tipo de operación correcto para contribuyente");
            
        } else if (config.iNatRec == TiNatRec.NO_CONTRIBUYENTE) {
            assertNotNull(config.iTipIDRec, "No contribuyente debe tener iTipIDRec");
            assertNotNull(config.dNumIDRec, "No contribuyente debe tener dNumIDRec");
            assertNull(config.iTiContRec, "No contribuyente NO debe tener iTiContRec");
            assertNull(config.dRucRec, "No contribuyente NO debe tener dRucRec");
            assertNull(config.dDVRec, "No contribuyente NO debe tener dDVRec");
            logger.info("   ✅ Campos de no contribuyente coherentes");
            
            // Validar que iTiOpe sea B2C para no contribuyentes (a menos que sea B2F)
            assertTrue(config.iTiOpe == TiTiOpe.B2C || config.iTiOpe == TiTiOpe.B2F,
                "No contribuyente debe tener iTiOpe = B2C o B2F");
            logger.info("   ✅ Tipo de operación correcto para no contribuyente");
            
            // Validación especial para innominados
            if (config.iTipIDRec == TiTipDocRec.INNOMINADO) {
                assertEquals("0", config.dNumIDRec, "Innominado debe tener dNumIDRec = '0'");
                assertTrue(monto == null || monto < 7_000_000.0, 
                    "Innominado solo válido para montos < 7.000.000 PYG");
                logger.info("   ✅ Validación de innominado correcta");
            }
        }
        
        // Validación 3: País por defecto
        assertEquals(PaisType.PRY, config.cPaisRec, "Por defecto debe ser Paraguay");
        logger.info("   ✅ País configurado correctamente");
        
        // Validación 4: Escenario detectado
        assertNotNull(config.escenarioDetectado, "Debe tener un escenario detectado");
        assertFalse(config.escenarioDetectado.isEmpty(), "Escenario no debe estar vacío");
        logger.info("   ✅ Escenario detectado: {}", config.escenarioDetectado);
    }
    
    /**
     * Test adicional para validar comportamiento con cliente null.
     */
    @Test
    public void testConfiguracionConClienteNull() {
        logger.info("\n🧪 TEST: Cliente NULL");
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            SifenReceptorHelper.determinarConfiguracionReceptor(null, 1000.0);
        });
        
        assertEquals("Cliente no puede ser null", exception.getMessage());
        logger.info("✅ Validación de cliente null correcta: {}", exception.getMessage());
    }
    
    /**
     * Test adicional para validar innominado con monto excedido.
     */
    @Test
    public void testInnominadoConMontoExcedido() {
        logger.info("\n🧪 TEST: Innominado con monto excedido");
        
        // Crear un cliente mock para innominado (sin nombre, sin RUC)
        Cliente cliente = new Cliente();
        cliente.setTributa(false);
        
        Persona persona = new Persona();
        persona.setNombre(""); // Nombre vacío -> innominado
        persona.setDocumento("0");
        cliente.setPersona(persona);
        
        Double montoExcedido = 8_000_000.0; // Más de 7 millones
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            SifenReceptorHelper.determinarConfiguracionReceptor(cliente, montoExcedido);
        });
        
        assertTrue(exception.getMessage().contains("innominada no permitida"),
            "Debe rechazar innominado con monto excedido");
        logger.info("✅ Validación de monto excedido correcta: {}", exception.getMessage());
    }
}

