package com.franco.dev.service.financiero.builder;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.sifen.service.SifenService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Punto de entrada unico para crear FacturaLegal + FacturaLegalItem + parciales
 * + descuento + SIFEN.
 *
 * Reemplaza la logica duplicada que existia en FacturaLegalGraphQL,
 * FacturaLegalApiService, FacturaService.crearFacturaLegalDesdeVenta, etc.
 *
 * El flujo respeta el orden de inserts original para no romper la replicacion
 * logica PG hacia central:
 *   1. INSERT factura_legal (con timbrado, sucursal, numero, sin parciales)
 *   2. INSERT factura_legal_item por cada descriptor (iva resuelto + producto/presentacion/ventaItem vinculados)
 *   3. UPDATE timbrado_detalle.numero_actual
 *   4. UPDATE factura_legal (con parciales recalculados desde items + descuento distribuido)
 *   5. (opcional) crearDocumentoElectronico (SIFEN) + UPDATE CDC en factura_legal
 */
@Service
public class FacturaLegalBuilder {

    private static final Logger log = LoggerFactory.getLogger(FacturaLegalBuilder.class);

    private final FacturaLegalService facturaLegalService;
    private final FacturaLegalItemService facturaLegalItemService;
    private final TimbradoDetalleService timbradoDetalleService;
    private final ProductoService productoService;
    private final VentaItemService ventaItemService;
    private final PresentacionService presentacionService;
    private final UsuarioService usuarioService;
    private final SifenService sifenService; // puede ser null si SIFEN desactivado

    public FacturaLegalBuilder(FacturaLegalService facturaLegalService,
                               FacturaLegalItemService facturaLegalItemService,
                               TimbradoDetalleService timbradoDetalleService,
                               ProductoService productoService,
                               VentaItemService ventaItemService,
                               PresentacionService presentacionService,
                               UsuarioService usuarioService,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) SifenService sifenService) {
        this.facturaLegalService = facturaLegalService;
        this.facturaLegalItemService = facturaLegalItemService;
        this.timbradoDetalleService = timbradoDetalleService;
        this.productoService = productoService;
        this.ventaItemService = ventaItemService;
        this.presentacionService = presentacionService;
        this.usuarioService = usuarioService;
        this.sifenService = sifenService;
    }

    @Transactional
    public FacturaLegal build(BuildRequest request) {
        FacturaLegal facturaLegal = request.getFacturaLegal();
        Long pdvId = request.getPdvId();
        List<FacturaLegalItemDescriptor> items = request.getItems();

        // 1. Resolver timbrado: si viene pre-resuelto en el request, usarlo; sino lookup por pdvId
        boolean sifenHabilitado = sifenService != null && sifenService.isSifenEnabled();
        TimbradoDetalle timbradoDetalle = request.getTimbradoDetalle();
        if (timbradoDetalle == null) {
            timbradoDetalle = timbradoDetalleService.getTimbradoDetalleActual(pdvId, sifenHabilitado);
        }
        if (timbradoDetalle == null) {
            String mensaje = sifenHabilitado
                    ? "SIFEN habilitado pero sin timbrado electronico activo para PDV " + pdvId
                    : "SIFEN deshabilitado pero sin timbrado no electronico activo para PDV " + pdvId;
            throw new GraphQLException(mensaje);
        }
        if (timbradoDetalle.getTimbrado() == null) {
            throw new GraphQLException("Timbrado detalle sin timbrado asociado para PDV " + pdvId);
        }
        boolean timbradoElectronico = Boolean.TRUE.equals(timbradoDetalle.getTimbrado().getIsElectronico());
        if (sifenHabilitado && !timbradoElectronico) {
            throw new GraphQLException("SIFEN habilitado, se requiere timbrado electronico activo para PDV " + pdvId);
        }
        if (!sifenHabilitado && timbradoElectronico) {
            throw new GraphQLException("SIFEN deshabilitado, se requiere timbrado no electronico activo para PDV " + pdvId);
        }

        facturaLegal.setTimbradoDetalle(timbradoDetalle);
        if (timbradoDetalle.getSucursal() == null || timbradoDetalle.getSucursal().getId() == null) {
            throw new GraphQLException("Timbrado detalle sin sucursal asignada");
        }
        facturaLegal.setSucursalId(timbradoDetalle.getSucursal().getId());

        Long numeroFactura = timbradoDetalle.getNumeroActual() != null
                ? timbradoDetalle.getNumeroActual() + 1
                : 1L;
        facturaLegal.setNumeroFactura(numeroFactura.intValue());

        // 2. INSERT factura_legal
        FacturaLegal facturaLegalGuardada = facturaLegalService.save(facturaLegal);

        // 3. INSERT factura_legal_item por descriptor
        List<ParcialesCalculator.ItemIvaTuple> tuples = new ArrayList<>();
        if (items != null) {
            for (FacturaLegalItemDescriptor d : items) {
                FacturaLegalItem item = construirItem(d, facturaLegalGuardada);
                facturaLegalItemService.save(item);
                tuples.add(new ParcialesCalculator.ItemIvaTuple(item.getIva(), item.getTotal()));
            }
        }

        // 4. UPDATE timbrado_detalle.numero_actual
        timbradoDetalle.setNumeroActual(numeroFactura);
        timbradoDetalleService.save(timbradoDetalle);

        // 5. UPDATE factura_legal con parciales calculados
        double descuento = facturaLegalGuardada.getDescuento() != null ? facturaLegalGuardada.getDescuento() : 0.0;
        ParcialesCalculator.Resultado resultado = ParcialesCalculator.calcular(tuples, descuento);
        facturaLegalGuardada.setTotalParcial0(resultado.getTotalParcial0());
        facturaLegalGuardada.setTotalParcial5(resultado.getTotalParcial5());
        facturaLegalGuardada.setTotalParcial10(resultado.getTotalParcial10());
        facturaLegalGuardada.setIvaParcial5(resultado.getIvaParcial5());
        facturaLegalGuardada.setIvaParcial10(resultado.getIvaParcial10());
        facturaLegalGuardada.setTotalFinal(resultado.getTotalFinal());
        facturaLegalGuardada = facturaLegalService.save(facturaLegalGuardada);

        // 6. SIFEN (opcional, no-lanzante si falla)
        if (request.isGenerarDE() && timbradoElectronico && sifenService != null) {
            try {
                log.info("Generando DE para factura ID: {}", facturaLegalGuardada.getId());
                DocumentoElectronico de = sifenService.crearDocumentoElectronico(facturaLegalGuardada);
                if (de != null && de.getCdc() != null) {
                    facturaLegalGuardada.setCdc(de.getCdc());
                    facturaLegalGuardada = facturaLegalService.save(facturaLegalGuardada);
                }
                log.info("DE generado para factura ID: {} CDC={}", facturaLegalGuardada.getId(),
                        de != null ? de.getCdc() : null);
            } catch (Exception e) {
                log.error("Error generando DE para factura ID: {}: {}", facturaLegalGuardada.getId(), e.getMessage(), e);
                // No relanzar: factura queda persistida, DE puede reintentarse
            }
        }

        return facturaLegalGuardada;
    }

    private FacturaLegalItem construirItem(FacturaLegalItemDescriptor d, FacturaLegal facturaLegalGuardada) {
        FacturaLegalItem item = new FacturaLegalItem();
        item.setFacturaLegal(facturaLegalGuardada);
        item.setSucursalId(facturaLegalGuardada.getSucursalId());
        item.setCantidad(d.getCantidad() != null ? d.getCantidad() : 1.0f);
        item.setDescripcion(d.getDescripcion() != null ? d.getDescripcion().toUpperCase() : null);
        item.setPrecioUnitario(d.getPrecioUnitario());

        // Total: si viene, usarlo; sino calcular
        Double total = d.getTotal();
        if (total == null && d.getPrecioUnitario() != null && d.getCantidad() != null) {
            total = d.getPrecioUnitario() * d.getCantidad();
        }
        item.setTotal(total != null ? total : 0.0);

        // Unidad de medida default
        if (d.getUnidadMedida() != null && !d.getUnidadMedida().trim().isEmpty()) {
            item.setUnidadMedida(d.getUnidadMedida().toUpperCase().trim());
        } else {
            item.setUnidadMedida("UNIDAD");
        }

        // Resolver entidades vinculadas (vincular siempre que se pueda — items futuros con FK)
        Producto producto = null;
        if (d.getProductoId() != null) {
            Optional<Producto> p = productoService.findById(d.getProductoId());
            if (p.isPresent()) {
                producto = p.get();
                item.setProducto(producto);
            }
        }
        VentaItem ventaItem = null;
        if (d.getVentaItemId() != null) {
            Optional<VentaItem> vi = ventaItemService.findById(d.getVentaItemId());
            if (vi.isPresent()) {
                ventaItem = vi.get();
                item.setVentaItem(ventaItem);
            }
        }
        Presentacion presentacion = null;
        if (d.getPresentacionId() != null) {
            Optional<Presentacion> pr = presentacionService.findById(d.getPresentacionId());
            if (pr.isPresent()) {
                presentacion = pr.get();
                item.setPresentacion(presentacion);
            }
        }

        // Lookup descripcion solo si todos los caminos previos fallan
        List<Producto> descMatches = null;
        if (d.getIva() == null
                && (producto == null || producto.getIva() == null)
                && (ventaItem == null || ventaItem.getProducto() == null || ventaItem.getProducto().getIva() == null)
                && (presentacion == null || presentacion.getProducto() == null || presentacion.getProducto().getIva() == null)
                && d.getDescripcion() != null) {
            descMatches = productoService.findByDescripcionNormalized(d.getDescripcion());
        }

        Integer iva = IvaResolver.resolveIva(
                d.getIva(), producto, ventaItem, presentacion,
                d.getDescripcion(), descMatches, log);
        item.setIva(iva);

        if (d.getUsuarioId() != null) {
            Optional<Usuario> usuario = usuarioService.findById(d.getUsuarioId());
            usuario.ifPresent(item::setUsuario);
        }

        return item;
    }
}
