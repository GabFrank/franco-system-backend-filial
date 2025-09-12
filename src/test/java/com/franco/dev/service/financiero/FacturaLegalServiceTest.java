package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.productos.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class FacturaLegalServiceTest {

    @Autowired
    private FacturaLegalService facturaLegalService;

    @Autowired
    private TimbradoDetalleService timbradoDetalleService;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FacturaLegalItemService facturaLegalItemService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;

    @Test
    @Transactional
    @Commit
    void testSaveFacturaLegal() {
        // ===== CONFIGURACIÓN DEL TEST =====
        // Parámetro para buscar factura existente (null para crear nueva)
        // Ejemplo: Long facturaExistenteId = 44127L; // Para usar factura existente
        Long facturaExistenteId = null; // Cambiar a un ID válido para buscar factura existente
        
        // El test creará productos aleatorios de la base de datos con sus precios reales
        
        FacturaLegal factura;
        
        if (facturaExistenteId != null) {
            // Buscar factura existente
            factura = facturaLegalService.findById(facturaExistenteId).orElse(null);
            assertNotNull(factura, "No se encontró la factura con ID: " + facturaExistenteId);
            System.out.println("Usando factura existente con ID: " + factura.getId());
        } else {
            // Crear nueva factura
            factura = crearNuevaFactura();
            factura = facturaLegalService.save(factura);
            System.out.println("Factura nueva creada con ID: " + factura.getId());
            System.out.println("CDC Generado: " + factura.getCdc());
        }
        
        // Crear items de factura
        crearItemsFactura(factura);
        
        // Recalcular totales de la factura
        recalcularTotalesFactura(factura);
        
        // Guardar factura actualizada
        FacturaLegal facturaActualizada = facturaLegalService.save(factura);
        
        System.out.println("Factura final:");
        System.out.println("ID: " + facturaActualizada.getId());
        System.out.println("Total Final: " + facturaActualizada.getTotalFinal());
        System.out.println("Total Parcial 10%: " + facturaActualizada.getTotalParcial10());
        System.out.println("IVA Parcial 10%: " + facturaActualizada.getIvaParcial10());
    }
    
    private FacturaLegal crearNuevaFactura() {
        // Datos de ejemplo basados en la base de datos:
        Long sucursalId = 6L;
        Long timbradoDetalleId = 74L;
        Long cajaId = 1989L;
        Long clienteId = 845L;
        Long usuarioId = 410L;
        String rucCliente = "80097276-7";

        // Obtener las entidades relacionadas
        TimbradoDetalle td = timbradoDetalleService.findById(timbradoDetalleId).orElse(null);
        Integer numeroFactura = td.getNumeroActual().intValue() + 1;
        PdvCaja caja = pdvCajaService.findById(cajaId).orElse(null);
        Cliente cliente = clienteService.findById(clienteId).orElse(null);
        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);

        assertNotNull(td, "El timbrado detalle no puede ser nulo");
        assertNotNull(caja, "La caja no puede ser nula");
        assertNotNull(cliente, "El cliente no puede ser nulo");
        assertNotNull(usuario, "El usuario no puede ser nulo");

        // Crear la entidad FacturaLegal sin totales (se calcularán después)
        FacturaLegal factura = new FacturaLegal();
        factura.setSucursalId(sucursalId);
        factura.setNumeroFactura(numeroFactura);
        factura.setFecha(LocalDateTime.now());
        factura.setNombre("SORIANO S.A");
        factura.setRuc(rucCliente);
        factura.setCredito(false);
        factura.setActivo(true);
        factura.setAutoimpreso(true);
        factura.setTimbradoDetalle(td);
        factura.setCaja(caja);
        factura.setCliente(cliente);
        factura.setUsuario(usuario);
        
        return factura;
    }
    
    private void crearItemsFactura(FacturaLegal factura) {
        // Crear items usando productos reales de la base de datos
        Usuario usuario = usuarioService.findById(410L).orElse(null);
        Random random = new Random();
        
        // Buscar productos aleatorios con precios
        List<Producto> productosDisponibles = buscarProductosConPrecios();
        
        if (productosDisponibles.isEmpty()) {
            System.out.println("No se encontraron productos con precios disponibles");
            return;
        }
        
        // Crear entre 2 y 4 items aleatorios
        int cantidadItems = 2 + random.nextInt(3); // 2, 3 o 4 items
        
        System.out.println("Items creados:");
        
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
            
            // Crear item de factura
            FacturaLegalItem item = new FacturaLegalItem();
            item.setFacturaLegal(factura);
            item.setSucursalId(6L);
            item.setCantidad(1.0f + random.nextInt(5)); // Cantidad entre 1 y 5
            item.setDescripcion(producto.getDescripcionFactura() != null ? 
                producto.getDescripcionFactura() : producto.getDescripcion());
            item.setPrecioUnitario(precio.getPrecio().doubleValue());
            item.setTotal(item.getCantidad() * item.getPrecioUnitario());
            item.setUsuario(usuario);
            item.setPresentacion(presentacion);
            
            facturaLegalItemService.save(item);
            
            System.out.println("- " + item.getDescripcion() + " x" + item.getCantidad() + 
                " @ " + item.getPrecioUnitario() + " = " + item.getTotal());
        }
    }
    
    private List<Producto> buscarProductosConPrecios() {
        // Buscar productos que tengan presentaciones con precios
        // Usamos una consulta simple para obtener productos activos
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
    
    private void recalcularTotalesFactura(FacturaLegal factura) {
        // Obtener todos los items de la factura
        List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(factura.getId());
        
        // Calcular totales
        double totalSinIva = items.stream()
            .mapToDouble(FacturaLegalItem::getTotal)
            .sum();
        
        // Asumiendo IVA del 10% (puedes ajustar según tu lógica de negocio)
        double ivaPorcentaje = 0.10;
        double ivaCalculado = totalSinIva * ivaPorcentaje;
        double totalConIva = totalSinIva + ivaCalculado;
        
        // Actualizar la factura
        factura.setTotalParcial10(totalSinIva);
        factura.setIvaParcial10(ivaCalculado);
        factura.setTotalFinal(totalConIva);
        
        System.out.println("Totales calculados:");
        System.out.println("Total sin IVA: " + totalSinIva);
        System.out.println("IVA (10%): " + ivaCalculado);
        System.out.println("Total Final: " + totalConIva);
    }
}
