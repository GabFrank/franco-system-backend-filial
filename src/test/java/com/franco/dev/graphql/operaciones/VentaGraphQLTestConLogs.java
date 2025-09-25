package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class VentaGraphQLTestConLogs {

    private static final Logger log = LoggerFactory.getLogger(VentaGraphQLTestConLogs.class);

    @Autowired
    private VentaGraphQL ventaGraphQL;

    @Autowired
    private FacturaLegalService facturaLegalService;

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    @Test
    @Transactional
    @Commit
    void testSaveVentaConFacturacionElectronica() {
        log.info("=== INICIANDO TEST DE FACTURACIÓN ELECTRÓNICA ===");
        
        // Configurar contexto de seguridad
        configurarSeguridad();
        
        // Configuración específica para el test
        String printerName = "ticket";
        String local = "Caja 1";
        Long pdvId = 25L; // PDV correcto para sucursal 24
        Boolean ticket = true;
        Boolean facturar = true;
        
        try {
            // 1. Crear datos de entrada basados en la venta 30353
            VentaInput ventaInput = crearVentaInputBasadoEn30353();
            List<VentaItemInput> ventaItemList = crearVentaItemListBasadoEn30353();
            CobroInput cobroInput = crearCobroInputBasadoEn30353();
            List<CobroDetalleInput> cobroDetalleList = crearCobroDetalleListBasadoEn30353();
            
            // 2. Calcular totales
            calcularTotalesBasadoEn30353(ventaInput, ventaItemList, cobroInput, cobroDetalleList);
            
            // 3. Ejecutar el método saveVenta
            log.info("Ejecutando saveVenta con facturación electrónica habilitada");
            log.info("Datos de entrada:");
            log.info("- Cliente ID: {}", ventaInput.getClienteId());
            log.info("- Usuario ID: {}", ventaInput.getUsuarioId());
            log.info("- Caja ID: {}", ventaInput.getCajaId());
            log.info("- Total GS: {}", ventaInput.getTotalGs());
            log.info("- Items: {}", ventaItemList.size());
            
            Venta ventaResultado = ventaGraphQL.saveVenta(
                ventaInput, 
                ventaItemList, 
                cobroInput, 
                cobroDetalleList, 
                ticket, 
                facturar, 
                printerName, 
                local, 
                pdvId, 
                null, // ventaCreditoInput
                null  // ventaCreditoCuotaInputList
            );
            
            log.info("=== RESULTADO DE LA VENTA ===");
            log.info("Venta creada exitosamente con ID: {}", ventaResultado != null ? ventaResultado.getId() : "null");
            
            // 4. Verificar resultado de la venta
            assertNotNull(ventaResultado, "La venta no debe ser nula");
            assertNotNull(ventaResultado.getId(), "La venta debe tener un ID");
            
            log.info("Venta ID: {}", ventaResultado.getId());
            log.info("Total GS: {}", ventaResultado.getTotalGs());
            log.info("Total RS: {}", ventaResultado.getTotalRs());
            log.info("Total DS: {}", ventaResultado.getTotalDs());
            log.info("Estado: {}", ventaResultado.getEstado());
            
            // 5. Verificar creación de FacturaLegal
            log.info("=== VERIFICANDO FACTURA LEGAL ===");
            FacturaLegal facturaLegal = facturaLegalService.findByVentaId(ventaResultado.getId());
            
            if (facturaLegal != null) {
                log.info("✅ FacturaLegal creada exitosamente:");
                log.info("- ID: {}", facturaLegal.getId());
                log.info("- Número: {}", facturaLegal.getNumeroFactura());
                log.info("- Cliente: {}", facturaLegal.getNombre());
                log.info("- RUC: {}", facturaLegal.getRuc());
                log.info("- Total: {}", facturaLegal.getTotalFinal());
                log.info("- Fecha: {}", facturaLegal.getCreadoEn());
                log.info("- Activo: {}", facturaLegal.getActivo());
                
                // 6. Verificar creación de DocumentoElectronico
                log.info("=== VERIFICANDO DOCUMENTO ELECTRÓNICO ===");
                DocumentoElectronico documentoElectronico = documentoElectronicoService.findByFacturaLegalIdAndSucursalId(facturaLegal.getId(), facturaLegal.getSucursalId());
                
                if (documentoElectronico != null) {
                    log.info("✅ DocumentoElectronico creado exitosamente:");
                    log.info("- ID: {}", documentoElectronico.getId());
                    log.info("- Factura Legal ID: {}", documentoElectronico.getFacturaLegal().getId());
                    log.info("- Estado: {}", documentoElectronico.getEstado());
                    log.info("- Tipo Documento: {}", documentoElectronico.getTipoDocumento());
                    log.info("- Fecha Creación: {}", documentoElectronico.getCreadoEn());
                    
                    if (documentoElectronico.getCdc() != null) {
                        log.info("- CDC: {}", documentoElectronico.getCdc());
                    }
                    
                    if (documentoElectronico.getUrlQr() != null) {
                        log.info("- URL QR: {}", documentoElectronico.getUrlQr());
                    }
                    
                } else {
                    log.warn("⚠️  DocumentoElectronico NO fue creado");
                    log.warn("Esto puede indicar un problema en el proceso de facturación electrónica o que la factura es autoimpresa.");
                }
                
            } else {
                log.warn("⚠️  FacturaLegal NO fue creada");
                log.warn("Esto puede indicar un problema en el proceso de facturación");
            }
            
            log.info("=== TEST COMPLETADO ===");

            try {
                log.info("Esperando 5 segundos para que la impresión del ticket finalice...");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("El hilo fue interrumpido mientras esperaba la impresión.", e);
            }
            
        } catch (Exception e) {
            log.error("Error durante el test de facturación electrónica: {}", e.getMessage(), e);
            throw new RuntimeException("Error en el test de facturación electrónica", e);
        }
    }
    
    private VentaInput crearVentaInputBasadoEn30353() {
        // Datos basados en la venta 30353 de la base de datos
        VentaInput ventaInput = new VentaInput();
        ventaInput.setClienteId(218L); // CARLOS EDUARDO GÓMEZ AVALOS
        ventaInput.setFormaPagoId(1L); // EFECTIVO
        ventaInput.setCajaId(580L); // Caja de la venta original
        ventaInput.setUsuarioId(250L); // Usuario de la venta original
        ventaInput.setCreadoEn(LocalDateTime.now());
        ventaInput.setSucursalId(24L); // Sucursal de la venta original
        
        log.info("VentaInput creado basado en venta 30353:");
        log.info("- Cliente ID: {} (CARLOS EDUARDO GÓMEZ AVALOS)", ventaInput.getClienteId());
        log.info("- Forma Pago ID: {} (EFECTIVO)", ventaInput.getFormaPagoId());
        log.info("- Caja ID: {}", ventaInput.getCajaId());
        log.info("- Usuario ID: {}", ventaInput.getUsuarioId());
        log.info("- Sucursal ID: {}", ventaInput.getSucursalId());
        
        return ventaInput;
    }
    
    private List<VentaItemInput> crearVentaItemListBasadoEn30353() {
        List<VentaItemInput> ventaItemList = new ArrayList<>();
        
        // Datos basados en el item de la venta 30353
        VentaItemInput item = new VentaItemInput();
        item.setProductoId(739L); // Producto de la venta original
        item.setPresentacionId(1049L); // Presentación de la venta original
        item.setCantidad(1.0); // Cantidad de la venta original
        item.setPrecio(19000.0); // Precio de la venta original
        item.setPrecioVenta(19000.0); // Precio de venta de la venta original
        item.setPrecioVentaId(1686L); // ID del precio de la venta original
        item.setValorDescuento(0.0); // Sin descuento
        item.setActivo(true);
        item.setUsuarioId(250L); // Usuario de la venta original
        item.setSucursalId(24L); // Sucursal de la venta original
        
        ventaItemList.add(item);
        
        log.info("VentaItemInput creado basado en venta 30353:");
        log.info("- Producto ID: {}", item.getProductoId());
        log.info("- Presentación ID: {}", item.getPresentacionId());
        log.info("- Cantidad: {}", item.getCantidad());
        log.info("- Precio: {}", item.getPrecio());
        log.info("- Precio Venta ID: {}", item.getPrecioVentaId());
        
        return ventaItemList;
    }
    
    private CobroInput crearCobroInputBasadoEn30353() {
        CobroInput cobroInput = new CobroInput();
        cobroInput.setUsuarioId(250L); // Usuario de la venta original
        cobroInput.setCreadoEn(LocalDateTime.now());
        // El total se calculará después
        
        log.info("CobroInput creado basado en venta 30353:");
        log.info("- Usuario ID: {}", cobroInput.getUsuarioId());
        
        return cobroInput;
    }
    
    private List<CobroDetalleInput> crearCobroDetalleListBasadoEn30353() {
        List<CobroDetalleInput> cobroDetalleList = new ArrayList<>();
        
        // Datos basados en el cobro detalle de la venta 30353
        CobroDetalleInput detalle = new CobroDetalleInput();
        detalle.setMonedaId(1L); // Guaraníes
        detalle.setFormaPagoId(1L); // EFECTIVO
        detalle.setCambio(1.0);
        detalle.setPago(true);
        detalle.setVuelto(false);
        detalle.setDescuento(false);
        detalle.setAumento(false);
        detalle.setUsuarioId(250L); // Usuario de la venta original
        detalle.setSucursalId(24L); // Sucursal de la venta original
        detalle.setCreadoEn(LocalDateTime.now());
        // El valor se calculará después
        
        cobroDetalleList.add(detalle);
        
        log.info("CobroDetalleInput creado basado en venta 30353:");
        log.info("- Moneda ID: {} (Guaraníes)", detalle.getMonedaId());
        log.info("- Forma Pago ID: {} (EFECTIVO)", detalle.getFormaPagoId());
        log.info("- Usuario ID: {}", detalle.getUsuarioId());
        log.info("- Sucursal ID: {}", detalle.getSucursalId());
        
        return cobroDetalleList;
    }
    
    private void calcularTotalesBasadoEn30353(VentaInput ventaInput, List<VentaItemInput> ventaItemList, CobroInput cobroInput, List<CobroDetalleInput> cobroDetalleList) {
        // Calcular totales basados en la venta 30353
        double totalGs = 19000.0; // Total de la venta original
        double totalRs = 15.2; // Total en reales de la venta original
        double totalDs = 2.71428571428571; // Total en dólares de la venta original
        
        // Actualizar venta
        ventaInput.setTotalGs(totalGs);
        ventaInput.setTotalRs(totalRs);
        ventaInput.setTotalDs(totalDs);
        
        // Actualizar cobro
        cobroInput.setTotalGs(totalGs);
        
        // Actualizar detalle de cobro con el valor calculado
        if (!cobroDetalleList.isEmpty()) {
            CobroDetalleInput detalle = cobroDetalleList.get(0);
            detalle.setValor(totalGs); // Establecer el valor del pago
        }
        
        log.info("Totales calculados basados en venta 30353:");
        log.info("- Total GS: {}", totalGs);
        log.info("- Total RS: {}", totalRs);
        log.info("- Total DS: {}", totalDs);
    }
    
    private void configurarSeguridad() {
        // Crear una autenticación simulada para el test
        List<SimpleGrantedAuthority> authorities = Arrays.asList(
            new SimpleGrantedAuthority("ROLE_USER"),
            new SimpleGrantedAuthority("ROLE_ADMIN")
        );
        
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            "testUser", 
            "testPassword", 
            authorities
        );
        
        // Establecer el contexto de seguridad
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        log.info("Contexto de seguridad configurado para el test");
    }
}
