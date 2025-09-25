package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Test simplificado para generar URL QR y compararla con la existente.
 * Este test utiliza datos reales del documento electrónico ID = 35.
 */
@Slf4j
public class SifenServiceQrSimpleTest {

    private SifenService sifenService;

    @BeforeEach
    void setUp() {
        // Crear instancia del servicio para testing
        sifenService = new SifenService(null, null, null, null, null, null);
    }

    @Test
    void testGenerarUrlQrDocumentoId35() {
        log.info("=== TEST: Generación de URL QR para Documento ID = 35 ===");
        
        // Datos reales obtenidos de la base de datos
        String cdcExistente = "01800994825021001000001722025092215695190140";
        String urlQrExistente = "https://ekuatia.set.gov.py/consultas/qr?nVersion=150&Id=01800994825021001000001722025092215695190140&dFeEmiDE=323032352d30392d32325431363a30323a3137&dRucRec=7199993&dTotGralOpe=19000&dTotIVA=1727&cItems=1&DigestValue=324f7a4155534830784a734a76757461517245374a70626d766a7462666851624f58786c723575344b2b383d&IdCSC=0001&cHashQR=7cb84d93517e0531055f557195716f31b5f0fd605b4da0ef30d0779611f8f4c3";
        
        // Crear objeto FacturaLegal con datos reales
        FacturaLegal facturaLegal = crearFacturaLegalConDatosReales();
        
        try {
            // Generar URL QR usando nuestro método
            String urlQrGenerada = sifenService.generarUrlQrLocal(cdcExistente, facturaLegal);
            
            log.info("=== RESULTADOS ===");
            log.info("CDC: {}", cdcExistente);
            log.info("URL QR Existente: {}", urlQrExistente);
            log.info("URL QR Generada:  {}", urlQrGenerada);
            log.info("¿Son iguales?: {}", urlQrExistente.equals(urlQrGenerada));
            
            // Análisis detallado de diferencias
            if (!urlQrExistente.equals(urlQrGenerada)) {
                analizarDiferencias(urlQrExistente, urlQrGenerada);
            } else {
                log.info("✅ ¡ÉXITO! Las URLs QR son idénticas.");
            }
            
        } catch (Exception e) {
            log.error("❌ Error al generar URL QR: {}", e.getMessage(), e);
        }
    }

    /**
     * Crea un objeto FacturaLegal con los datos reales del documento ID = 35
     */
    private FacturaLegal crearFacturaLegalConDatosReales() {
        FacturaLegal facturaLegal = new FacturaLegal();
        
        // Datos básicos
        facturaLegal.setId(11834L);
        facturaLegal.setNumeroFactura(17);
        facturaLegal.setFecha(LocalDateTime.of(2025, 9, 22, 19, 2, 17)); // 2025-09-22T19:02:17
        
        // Totales
        facturaLegal.setTotalFinal(19000.0);
        facturaLegal.setIvaParcial0(null);
        facturaLegal.setIvaParcial5(0.0);
        facturaLegal.setIvaParcial10(1727.27272727273);
        
        // Cliente
        Cliente cliente = new Cliente();
        Persona persona = new Persona();
        persona.setDocumento("7199993");
        persona.setNombre("CARLOS EDUARDO GÓMEZ AVALOS");
        cliente.setPersona(persona);
        facturaLegal.setCliente(cliente);
        
        return facturaLegal;
    }

    /**
     * Analiza las diferencias entre la URL QR existente y la generada
     */
    private void analizarDiferencias(String urlExistente, String urlGenerada) {
        log.info("=== ANÁLISIS DE DIFERENCIAS ===");
        
        try {
            // Extraer parámetros de ambas URLs
            String[] existenteParams = urlExistente.split("\\?")[1].split("&");
            String[] generadaParams = urlGenerada.split("\\?")[1].split("&");
            
            log.info("Parámetros URL Existente:");
            for (String param : existenteParams) {
                log.info("  {}", param);
            }
            
            log.info("Parámetros URL Generada:");
            for (String param : generadaParams) {
                log.info("  {}", param);
            }
            
            // Comparar parámetro por parámetro
            log.info("=== COMPARACIÓN PARÁMETRO POR PARÁMETRO ===");
            for (int i = 0; i < Math.min(existenteParams.length, generadaParams.length); i++) {
                String[] existenteParam = existenteParams[i].split("=");
                String[] generadaParam = generadaParams[i].split("=");
                
                if (existenteParam.length == 2 && generadaParam.length == 2) {
                    String nombre = existenteParam[0];
                    String valorExistente = existenteParam[1];
                    String valorGenerado = generadaParam[1];
                    
                    boolean igual = valorExistente.equals(valorGenerado);
                    log.info("{}: {} vs {} = {}", 
                        nombre, valorExistente, valorGenerado, 
                        igual ? "✅ IGUAL" : "❌ DIFERENTE");
                        
                    if (!igual) {
                        log.warn("⚠️  Diferencia encontrada en parámetro: {}", nombre);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error al analizar diferencias: {}", e.getMessage(), e);
        }
    }

    /**
     * Test para verificar la codificación de fecha
     */
    @Test
    void testCodificacionFecha() {
        log.info("=== TEST: Codificación de Fecha ===");
        
        LocalDateTime fecha = LocalDateTime.of(2025, 9, 22, 19, 2, 17);
        String fechaCodificada = sifenService.codificarFechaEmision(fecha);
        String fechaEsperada = "323032352d30392d32325431363a30323a3137"; // Valor de la URL existente
        
        log.info("Fecha original: {}", fecha);
        log.info("Fecha codificada: {}", fechaCodificada);
        log.info("Fecha esperada:   {}", fechaEsperada);
        log.info("¿Son iguales?: {}", fechaEsperada.equals(fechaCodificada));
        
        if (!fechaEsperada.equals(fechaCodificada)) {
            log.warn("⚠️  La codificación de fecha no coincide");
            log.info("Diferencia: '{}' vs '{}'", fechaEsperada, fechaCodificada);
        }
    }

    /**
     * Test para verificar el cálculo de totales
     */
    @Test
    void testCalculoTotales() {
        log.info("=== TEST: Cálculo de Totales ===");
        
        FacturaLegal facturaLegal = crearFacturaLegalConDatosReales();
        
        // Calcular totales como lo hace nuestro método
        BigDecimal totalGral = facturaLegal.getTotalFinal() != null ? BigDecimal.valueOf(facturaLegal.getTotalFinal()) : BigDecimal.ZERO;
        BigDecimal totalIva = BigDecimal.ZERO;
        if (facturaLegal.getIvaParcial10() != null) totalIva = totalIva.add(BigDecimal.valueOf(facturaLegal.getIvaParcial10()));
        if (facturaLegal.getIvaParcial5() != null) totalIva = totalIva.add(BigDecimal.valueOf(facturaLegal.getIvaParcial5()));
        if (facturaLegal.getIvaParcial0() != null) totalIva = totalIva.add(BigDecimal.valueOf(facturaLegal.getIvaParcial0()));
        
        // Convertir a centavos
        int totalGralCentavos = totalGral.multiply(new BigDecimal("100")).intValue();
        int totalIvaCentavos = totalIva.multiply(new BigDecimal("100")).intValue();
        
        log.info("Total General: {} -> {} centavos", totalGral, totalGralCentavos);
        log.info("Total IVA: {} -> {} centavos", totalIva, totalIvaCentavos);
        log.info("Total General esperado: 19000 centavos");
        log.info("Total IVA esperado: 1727 centavos");
        
        boolean totalGralCorrecto = totalGralCentavos == 19000;
        boolean totalIvaCorrecto = totalIvaCentavos == 1727;
        
        log.info("Total General correcto: {}", totalGralCorrecto ? "✅" : "❌");
        log.info("Total IVA correcto: {}", totalIvaCorrecto ? "✅" : "❌");
    }

    /**
     * Test manual para ejecutar desde main
     */
    public static void main(String[] args) {
        SifenServiceQrSimpleTest test = new SifenServiceQrSimpleTest();
        test.setUp();
        
        System.out.println("=== EJECUTANDO TEST MANUAL ===");
        test.testGenerarUrlQrDocumentoId35();
        System.out.println("\n=== EJECUTANDO TEST DE CODIFICACIÓN DE FECHA ===");
        test.testCodificacionFecha();
        System.out.println("\n=== EJECUTANDO TEST DE CÁLCULO DE TOTALES ===");
        test.testCalculoTotales();
    }
}
