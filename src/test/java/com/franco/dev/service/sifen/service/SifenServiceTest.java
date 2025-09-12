package com.franco.dev.service.sifen.service;

import com.franco.dev.service.sifen.dto.response.ConsultaRucResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clase de prueba para SifenService
 * 
 * Esta clase contiene tests para verificar el funcionamiento correcto
 * del método consultaRuc del SifenService.
 * 
 * IMPORTANTE: Estos tests realizan llamadas reales al servicio SIFEN,
 * por lo que requieren configuración válida y conexión a internet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class SifenServiceTest {

    private static final Logger log = LoggerFactory.getLogger(SifenServiceTest.class);

    @Autowired
    private SifenService sifenService;

    @BeforeEach
    void setUp() {
        log.info("=== INICIANDO TEST DE SIFEN SERVICE ===");
    }

    @Test
    @DisplayName("Debería consultar RUC válido exitosamente")
    void testConsultaRucValido() {
        // RUC de prueba válido (ejemplo: RUC de una empresa conocida)
        String rucValido = "80099482-5";
        
        log.info("Probando consulta RUC válido: {}", rucValido);
        
        ConsultaRucResponse response = sifenService.consultaRuc(rucValido);
        
        // Verificaciones básicas
        assertNotNull(response, "La respuesta no debe ser null");
        assertNotNull(response.getRuc(), "El RUC en la respuesta no debe ser null");
        assertNotNull(response.getCodigoRespuesta(), "El código de respuesta no debe ser null");
        assertNotNull(response.getMensajeRespuesta(), "El mensaje de respuesta no debe ser null");
        
        // cambiar log por sysout y quitar log.info
        System.out.println("Respuesta recibida:");
        System.out.println("- RUC: " + response.getRuc());
        System.out.println("- Procesamiento correcto: " + response.isProcesamientoCorrecto());
        System.out.println("- Código respuesta: " + response.getCodigoRespuesta());
        System.out.println("- Mensaje respuesta: " + response.getMensajeRespuesta());
        System.out.println("- Razón social: " + response.getRazonSocial());
        System.out.println("- Estado contribuyente: " + response.getEstadoContribuyente());
        System.out.println("- Es facturador electrónico: " + response.getEsFacturadorElectronico());    

        
        // Si el procesamiento fue correcto, verificar campos adicionales
        if (response.isProcesamientoCorrecto()) {
            assertNotNull(response.getRazonSocial(), "La razón social no debe ser null para RUC válido");
            assertNotNull(response.getEstadoContribuyente(), "El estado del contribuyente no debe ser null");
        }
    }

    @Test
    @DisplayName("Debería manejar RUC inválido correctamente")
    void testConsultaRucInvalido() {
        // RUC inválido (formato incorrecto)
        String rucInvalido = "123456789";
        
        log.info("Probando consulta RUC inválido: {}", rucInvalido);
        
        ConsultaRucResponse response = sifenService.consultaRuc(rucInvalido);
        
        // Verificaciones básicas
        assertNotNull(response, "La respuesta no debe ser null");
        assertNotNull(response.getRuc(), "El RUC en la respuesta no debe ser null");
        assertNotNull(response.getCodigoRespuesta(), "El código de respuesta no debe ser null");
        assertNotNull(response.getMensajeRespuesta(), "El mensaje de respuesta no debe ser null");
        
        log.info("Respuesta para RUC inválido:");
        log.info("- RUC: {}", response.getRuc());
        log.info("- Procesamiento correcto: {}", response.isProcesamientoCorrecto());
        log.info("- Código respuesta: {}", response.getCodigoRespuesta());
        log.info("- Mensaje respuesta: {}", response.getMensajeRespuesta());
        
        // Para RUC inválido, esperamos que el procesamiento no sea correcto
        // o que tenga un código de error específico
        assertFalse(response.isProcesamientoCorrecto(), 
            "El procesamiento no debería ser correcto para RUC inválido");
    }

    @Test
    @DisplayName("Debería manejar RUC con guión correctamente")
    void testConsultaRucConGuion() {
        // RUC con guión y dígito verificador
        String rucConGuion = "80012345-7";
        
        log.info("Probando consulta RUC con guión: {}", rucConGuion);
        
        ConsultaRucResponse response = sifenService.consultaRuc(rucConGuion);
        
        assertNotNull(response, "La respuesta no debe ser null");
        assertEquals(rucConGuion, response.getRuc(), 
            "El RUC en la respuesta debe mantener el formato original");
        
        log.info("Respuesta para RUC con guión:");
        log.info("- RUC original: {}", rucConGuion);
        log.info("- RUC en respuesta: {}", response.getRuc());
        log.info("- Procesamiento correcto: {}", response.isProcesamientoCorrecto());
    }

    @Test
    @DisplayName("Debería manejar RUC sin guión correctamente")
    void testConsultaRucSinGuion() {
        // RUC sin guión (solo números)
        String rucSinGuion = "800123457";
        
        log.info("Probando consulta RUC sin guión: {}", rucSinGuion);
        
        ConsultaRucResponse response = sifenService.consultaRuc(rucSinGuion);
        
        assertNotNull(response, "La respuesta no debe ser null");
        assertEquals(rucSinGuion, response.getRuc(), 
            "El RUC en la respuesta debe mantener el formato original");
        
        log.info("Respuesta para RUC sin guión:");
        log.info("- RUC original: {}", rucSinGuion);
        log.info("- RUC en respuesta: {}", response.getRuc());
        log.info("- Procesamiento correcto: {}", response.isProcesamientoCorrecto());
    }

    @Test
    @DisplayName("Debería manejar RUC null correctamente")
    void testConsultaRucNull() {
        log.info("Probando consulta RUC null");
        
        ConsultaRucResponse response = sifenService.consultaRuc(null);
        
        assertNotNull(response, "La respuesta no debe ser null");
        assertFalse(response.isProcesamientoCorrecto(), 
            "El procesamiento no debería ser correcto para RUC null");
        
        log.info("Respuesta para RUC null:");
        log.info("- Procesamiento correcto: {}", response.isProcesamientoCorrecto());
        log.info("- Código respuesta: {}", response.getCodigoRespuesta());
        log.info("- Mensaje respuesta: {}", response.getMensajeRespuesta());
    }

    @Test
    @DisplayName("Debería manejar RUC vacío correctamente")
    void testConsultaRucVacio() {
        String rucVacio = "";
        
        log.info("Probando consulta RUC vacío: '{}'", rucVacio);
        
        ConsultaRucResponse response = sifenService.consultaRuc(rucVacio);
        
        assertNotNull(response, "La respuesta no debe ser null");
        assertFalse(response.isProcesamientoCorrecto(), 
            "El procesamiento no debería ser correcto para RUC vacío");
        
        log.info("Respuesta para RUC vacío:");
        log.info("- Procesamiento correcto: {}", response.isProcesamientoCorrecto());
        log.info("- Código respuesta: {}", response.getCodigoRespuesta());
        log.info("- Mensaje respuesta: {}", response.getMensajeRespuesta());
    }

    @Test
    @DisplayName("Debería manejar RUC con caracteres especiales correctamente")
    void testConsultaRucConCaracteresEspeciales() {
        // RUC con caracteres especiales que deberían ser limpiados
        String rucConEspeciales = "800-123-45-7";
        
        log.info("Probando consulta RUC con caracteres especiales: {}", rucConEspeciales);
        
        ConsultaRucResponse response = sifenService.consultaRuc(rucConEspeciales);
        
        assertNotNull(response, "La respuesta no debe ser null");
        assertEquals(rucConEspeciales, response.getRuc(), 
            "El RUC en la respuesta debe mantener el formato original");
        
        log.info("Respuesta para RUC con caracteres especiales:");
        log.info("- RUC original: {}", rucConEspeciales);
        log.info("- RUC en respuesta: {}", response.getRuc());
        log.info("- Procesamiento correcto: {}", response.isProcesamientoCorrecto());
    }

    @Test
    @DisplayName("Debería verificar campos de respuesta para RUC válido")
    void testVerificarCamposRespuesta() {
        // Usar un RUC conocido válido para verificar todos los campos
        String rucValido = "80012345-7";
        
        log.info("Verificando campos de respuesta para RUC: {}", rucValido);
        
        ConsultaRucResponse response = sifenService.consultaRuc(rucValido);
        
        // Verificar campos de la clase base
        assertNotNull(response.getTimestamp(), "El timestamp no debe ser null");
        
        // Verificar campos específicos de ConsultaRucResponse
        assertNotNull(response.getRuc(), "El RUC no debe ser null");
        
        // Si el procesamiento fue correcto, verificar campos adicionales
        if (response.isProcesamientoCorrecto()) {
            log.info("Verificando campos para procesamiento correcto:");
            log.info("- Razón social: {}", response.getRazonSocial());
            log.info("- Estado contribuyente: {}", response.getEstadoContribuyente());
            log.info("- Código estado contribuyente: {}", response.getCodigoEstadoContribuyente());
            log.info("- Es facturador electrónico: {}", response.getEsFacturadorElectronico());
            
            // Campos opcionales que pueden estar presentes
            log.info("- Mensaje procesamiento: {}", response.getMensajeProcesamiento());
            log.info("- DV: {}", response.getDv());
            log.info("- Estado: {}", response.getEstado());
            log.info("- Nombre: {}", response.getNombre());
            log.info("- Nombre fantasía: {}", response.getNombreFantasia());
            log.info("- Teléfono: {}", response.getTelefono());
            log.info("- Dirección: {}", response.getDireccion());
            log.info("- Código establecimiento: {}", response.getCodigoEstablecimiento());
            log.info("- Validación correcta: {}", response.getValidacionCorrecta());
            log.info("- Mensaje validación: {}", response.getMensajeValidacion());
        }
        
        log.info("=== FIN DE VERIFICACIÓN DE CAMPOS ===");
    }
}
