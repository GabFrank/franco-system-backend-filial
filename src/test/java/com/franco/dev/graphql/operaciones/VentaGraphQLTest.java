package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.productos.ProductoService;
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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class VentaGraphQLTest {

    private static final Logger log = LoggerFactory.getLogger(VentaGraphQLTest.class);

    @Autowired
    private VentaGraphQL ventaGraphQL;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;



    @Test
    @Transactional
    @Commit
    void testSaveVenta() {
        // ===== CONFIGURACIÓN DEL TEST =====
        // Configurar contexto de seguridad para el test
        configurarSeguridad();
        
        // Configuración específica para el test
        String printerName = "ticket";
        String local = "Caja 1";
        Long pdvId = 4L;
        Boolean ticket = true; // Deshabilitar impresión para evitar problemas con impresoras
        Boolean facturar = true;
        Long sucursalId = 6L;
        
        System.out.println("=== INICIANDO TEST DE VENTA ===");
        System.out.println("Configuración:");
        System.out.println("- Printer: " + printerName);
        System.out.println("- Local: " + local);
        System.out.println("- PDV ID: " + pdvId);
        System.out.println("- Ticket: " + ticket);
        System.out.println("- Facturar: " + facturar);
        
        try {
            // Debuggear impresoras disponibles
            debuggearImpresorasDisponibles(printerName);
            
            // 1. Crear datos de entrada
            VentaInput ventaInput = crearVentaInput();
            List<VentaItemInput> ventaItemList = crearVentaItemList();
            CobroInput cobroInput = crearCobroInput();
            List<CobroDetalleInput> cobroDetalleList = crearCobroDetalleList();
            
            // 2. Calcular totales
            calcularTotales(ventaInput, ventaItemList, cobroInput, cobroDetalleList);
            
            // 3. Ejecutar el método saveVenta
            log.info("Ejecutando saveVenta con {} items", ventaItemList.size());
            log.debug("VentaInput: {}", ventaInput);
            log.debug("CobroInput: {}", cobroInput);
            
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
            
            log.info("Venta creada exitosamente con ID: {}", ventaResultado != null ? ventaResultado.getId() : "null");
            
            // 4. Verificar resultado
            assertNotNull(ventaResultado, "La venta no debe ser nula");
            assertNotNull(ventaResultado.getId(), "La venta debe tener un ID");
            
            System.out.println("=== TEST EXITOSO ===");
            System.out.println("Venta creada con ID: " + ventaResultado.getId());
            System.out.println("Total GS: " + ventaResultado.getTotalGs());
            System.out.println("Total RS: " + ventaResultado.getTotalRs());
            System.out.println("Total DS: " + ventaResultado.getTotalDs());
            System.out.println("Estado: " + ventaResultado.getEstado());
            
        } catch (Exception e) {
            System.err.println("Error durante el test: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error en el test de venta", e);
        }
    }
    
    private VentaInput crearVentaInput() {
        // Datos de ejemplo basados en la base de datos
        Long clienteId = 0L; // Cliente genérico "SIN NOMBRE"
        Long formaPagoId = 1L; // EFECTIVO
        Long cajaId = 1989L; // Caja activa
        Long usuarioId = 410L; // Usuario válido
        Long sucursalId = 6L;
        
        VentaInput ventaInput = new VentaInput();
        ventaInput.setClienteId(clienteId);
        ventaInput.setFormaPagoId(formaPagoId);
        ventaInput.setCajaId(cajaId);
        ventaInput.setUsuarioId(usuarioId);
        ventaInput.setCreadoEn(LocalDateTime.now());
        ventaInput.setSucursalId(sucursalId);
        
        System.out.println("VentaInput creado:");
        System.out.println("- Cliente ID: " + clienteId);
        System.out.println("- Forma Pago ID: " + formaPagoId);
        System.out.println("- Caja ID: " + cajaId);
        System.out.println("- Usuario ID: " + usuarioId);
        
        return ventaInput;
    }
    
    private List<VentaItemInput> crearVentaItemList() {
        List<VentaItemInput> ventaItemList = new ArrayList<>();
        Random random = new Random();
        
        // Buscar productos aleatorios con precios
        List<Producto> productosDisponibles = buscarProductosConPrecios();
        
        if (productosDisponibles.isEmpty()) {
            System.out.println("No se encontraron productos con precios disponibles");
            return ventaItemList;
        }
        
        // Crear entre 2 y 4 items aleatorios
        int cantidadItems = 2 + random.nextInt(3); // 2, 3 o 4 items
        
        System.out.println("Creando " + cantidadItems + " items de venta:");
        
        for (int i = 0; i < cantidadItems; i++) {
            // Seleccionar producto aleatorio
            Producto producto = productosDisponibles.get(random.nextInt(productosDisponibles.size()));
            
            // Obtener presentación principal del producto
            Presentacion presentacion = presentacionService.findByPrincipalAndProductoId(true, producto.getId());
            if (presentacion == null) {
                System.out.println("No se encontró presentación principal para producto: " + producto.getDescripcion());
                continue;
            }
            
            // Obtener precio de la presentación
            PrecioPorSucursal precio = precioPorSucursalService.findPrincipalByPrecionacionId(presentacion.getId());
            if (precio == null) {
                System.out.println("No se encontró precio para presentación: " + presentacion.getId());
                continue;
            }
            
            // Crear item de venta
            VentaItemInput item = new VentaItemInput();
            item.setProductoId(producto.getId());
            item.setProductoDescripcion(producto.getDescripcion());
            item.setPresentacionId(presentacion.getId());
            item.setPresentacionDescripcion(presentacion.getDescripcion());
            item.setCantidad(1.0 + random.nextInt(5)); // Cantidad entre 1 y 5
            item.setPrecio(precio.getPrecio().doubleValue());
            item.setPrecioVenta(precio.getPrecio().doubleValue());
            item.setPrecioVentaId(precio.getId()); // ID del precio en precio_por_sucursal
            item.setValorDescuento(0.0); // Sin descuento por ahora
            item.setActivo(true);
            item.setUsuarioId(410L);
            item.setSucursalId(6L);
            
            ventaItemList.add(item);
            
            System.out.println("- " + item.getProductoDescripcion() + " x" + item.getCantidad() + 
                " @ " + item.getPrecioVenta() + " = " + (item.getCantidad() * item.getPrecioVenta()));
        }
        
        return ventaItemList;
    }
    
    private CobroInput crearCobroInput() {
        CobroInput cobroInput = new CobroInput();
        cobroInput.setUsuarioId(410L);
        cobroInput.setCreadoEn(LocalDateTime.now());
        // El total se calculará después
        
        System.out.println("CobroInput creado");
        return cobroInput;
    }
    
    private List<CobroDetalleInput> crearCobroDetalleList() {
        List<CobroDetalleInput> cobroDetalleList = new ArrayList<>();
        
        // Crear un detalle de pago en efectivo
        CobroDetalleInput detalle = new CobroDetalleInput();
        detalle.setMonedaId(1L); // Guaraníes
        detalle.setFormaPagoId(1L); // EFECTIVO
        detalle.setCambio(1.0);
        detalle.setPago(true);
        detalle.setVuelto(false);
        detalle.setDescuento(false);
        detalle.setAumento(false);
        detalle.setUsuarioId(410L);
        detalle.setSucursalId(6L);
        detalle.setCreadoEn(LocalDateTime.now());
        // El valor se calculará después
        
        cobroDetalleList.add(detalle);
        
        System.out.println("CobroDetalleInput creado - Pago en efectivo");
        return cobroDetalleList;
    }
    
    private void calcularTotales(VentaInput ventaInput, List<VentaItemInput> ventaItemList, CobroInput cobroInput, List<CobroDetalleInput> cobroDetalleList) {
        // Calcular totales de la venta
        double totalGs = ventaItemList.stream()
            .mapToDouble(item -> item.getCantidad() * item.getPrecioVenta())
            .sum();
        
        // Asumiendo tipos de cambio básicos (puedes ajustar según tu lógica)
        double cambioRs = 1300.0; // Guaraníes por Real
        double cambioDs = 6900.0; // Guaraníes por Dólar
        
        double totalRs = totalGs / cambioRs;
        double totalDs = totalGs / cambioDs;
        
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
        
        System.out.println("Totales calculados:");
        System.out.println("- Total GS: " + totalGs);
        System.out.println("- Total RS: " + String.format("%.2f", totalRs));
        System.out.println("- Total DS: " + String.format("%.2f", totalDs));
    }
    
    private List<Producto> buscarProductosConPrecios() {
        // Buscar productos que tengan presentaciones con precios
        List<Producto> todosProductos = productoService.findAll2();
        
        // Filtrar productos que tengan presentaciones con precios
        return todosProductos.stream()
            .filter(producto -> producto.getActivo() != null && producto.getActivo())
            .filter(producto -> {
                Presentacion presentacion = presentacionService.findByPrincipalAndProductoId(true, producto.getId());
                if (presentacion == null) return false;
                
                PrecioPorSucursal precio = precioPorSucursalService.findPrincipalByPrecionacionId(presentacion.getId());
                return precio != null && precio.getPrecio() != null && precio.getPrecio().doubleValue() > 0;
            })
            .limit(20) // Limitar a 20 productos para evitar consultas muy largas
            .collect(java.util.stream.Collectors.toList());
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
        
        System.out.println("Contexto de seguridad configurado para el test");
    }
    
    private void debuggearImpresorasDisponibles(String printerName) {
        log.info("=== DEBUGGEANDO IMPRESORAS DISPONIBLES ===");
        
        try {
            // Usar PrinterOutputStream para obtener impresoras
            String[] impresorasDisponibles = com.franco.dev.utilitarios.print.output.PrinterOutputStream.getListPrintServicesNames();
            
            log.info("Total de impresoras encontradas: {}", impresorasDisponibles.length);
            
            if (impresorasDisponibles.length == 0) {
                log.warn("⚠️  NO SE ENCONTRARON IMPRESORAS EN EL SISTEMA");
                log.warn("Esto puede indicar que:");
                log.warn("1. CUPS no está ejecutándose");
                log.warn("2. Java no tiene acceso a CUPS");
                log.warn("3. Las impresoras no están configuradas correctamente");
            } else {
                log.info("📋 Impresoras disponibles:");
                for (int i = 0; i < impresorasDisponibles.length; i++) {
                    String impresora = impresorasDisponibles[i];
                    boolean esLaBuscada = impresora.equals(printerName);
                    log.info("  {}. {} {}", i + 1, impresora, esLaBuscada ? "← TARGET" : "");
                }
                
                // Buscar la impresora específica
                javax.print.PrintService impresoraTarget = com.franco.dev.utilitarios.print.output.PrinterOutputStream.getPrintServiceByName(printerName);
                if (impresoraTarget != null) {
                    log.info("✅ Impresora '{}' encontrada correctamente", printerName);
                    log.info("   - Nombre: {}", impresoraTarget.getName());
                    log.info("   - Atributos: {}", impresoraTarget.getAttributes());
                } else {
                    log.error("❌ Impresora '{}' NO encontrada", printerName);
                    log.error("Verifica que la impresora esté instalada en CUPS");
                }
            }
            
            // Verificar CUPS desde línea de comandos
            verificarCUPSDesdeComando();
            
        } catch (Exception e) {
            log.error("Error al debuggear impresoras: {}", e.getMessage(), e);
        }
        
        log.info("=== FIN DEBUG IMPRESORAS ===");
    }
    
    private void verificarCUPSDesdeComando() {
        try {
            log.info("🔍 Verificando CUPS desde línea de comandos...");
            
            // Ejecutar lpstat -p para ver impresoras disponibles
            ProcessBuilder pb = new ProcessBuilder("lpstat", "-p");
            Process process = pb.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("✅ CUPS está funcionando. Impresoras del sistema:");
                log.info("{}", output.toString());
            } else {
                log.warn("⚠️  CUPS no está disponible o no responde");
            }
            
        } catch (Exception e) {
            log.warn("No se pudo verificar CUPS desde comando: {}", e.getMessage());
        }
    }
    
    @Test
    void testDebugImpresoras() {
        log.info("=== TEST ESPECÍFICO PARA DEBUGGEAR IMPRESORAS ===");
        
        String printerName = "ticket_soporte";
        debuggearImpresorasDisponibles(printerName);
        
        // Intentar obtener la impresora directamente
        try {
            javax.print.PrintService impresora = com.franco.dev.utilitarios.print.output.PrinterOutputStream.getPrintServiceByName(printerName);
            if (impresora != null) {
                log.info("✅ ÉXITO: Impresora encontrada y disponible para testing");
                log.info("   Nombre: {}", impresora.getName());
                log.info("   Estado: {}", impresora.getAttributes());
            } else {
                log.error("❌ FALLO: Impresora no encontrada");
                log.error("Soluciones sugeridas:");
                log.error("1. Verificar que CUPS esté ejecutándose: sudo systemctl status cups");
                log.error("2. Verificar que la impresora esté instalada: lpstat -p");
                log.error("3. Reiniciar CUPS: sudo systemctl restart cups");
                log.error("4. Verificar permisos de Java para acceder a CUPS");
            }
        } catch (Exception e) {
            log.error("Error al acceder a la impresora: {}", e.getMessage(), e);
        }
        
        log.info("=== FIN TEST DEBUG IMPRESORAS ===");
    }
}
