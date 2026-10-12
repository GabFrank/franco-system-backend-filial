package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.*;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.*;
import com.franco.dev.service.utils.ImageService;
import graphql.kickstart.tools.GraphQLResolver;
import kotlin.collections.ArrayDeque;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProductoResolver implements GraphQLResolver<Producto> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SubFamiliaService subFamiliaService;

    @Autowired
    private IngredienteService ingredienteService;

    @Autowired
    private ProductoIngredienteService productoIngredienteService;

    @Autowired
    private CostosPorProductoService costosPorProductoService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private ProductoImagenService productoImagenService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PresentacionResolver presentacionResolver;

    @Autowired
    private PrecioEspecialLector precioEspecialLector;

    public Usuario usuario(Producto e){
        if(e.getUsuario()!=null) {
            return usuarioService.findById(e.getUsuario().getId()).orElse(null);
        } else {
            return null;
        }
    }

    public TipoConservacion tipoConservacion(Producto e){ return e.getTipoConservacion(); }

    public List<ProductoIngrediente> ingredientesList(Producto p){
        List<Ingrediente> ingredienteList = new ArrayDeque<>();
        List<ProductoIngrediente> productoIngredienteList = productoIngredienteService.findByProducto(p.getId());
        for(ProductoIngrediente pi : productoIngredienteList){
            ingredienteList.add(ingredienteService.findById(pi.getIngrediente().getId()).orElse(null));
        }
        return productoIngredienteList;
    }

    public Float existenciaTotal(Producto p){
        return movimientoStockService.stockByProductoId(p.getId());
    }

    public List<ProductoCompra> productoUltimasCompras(Producto p){
        List<ProductoCompra> pcList = new ArrayList<>();
        Pedido pedido;
        PedidoItem pedidoItem;
        List<MovimientoStock> msList = movimientoStockService.ultimosMovimientos(p.getId(), TipoMovimiento.COMPRA, 5);
        for (MovimientoStock ms : msList){
            ProductoCompra pc = new ProductoCompra();
            pc.setCantidad(ms.getCantidad());
            pc.setCreadoEn(ms.getCreadoEn());
            pc.setPedido(pedidoService.findById(ms.getReferencia()).orElse(null));
            CostoPorProducto cps = costosPorProductoService.findByMovimientoStockId(ms.getId());
            if(cps!=null){
                pc.setPrecio(cps.getUltimoPrecioCompra());
            }
            pcList.add(pc);
        }
        return pcList;
    }

    public List<Presentacion> presentaciones(Producto p){
        return precioEspecialLector.habilitarPresentaciones(presentacionService.findByProductoId(p.getId()));
    }

    /** 250x250, para listas. Mismo campo que el central: el escritorio consulta a los dos. */
    public String imagenPrincipalMiniatura(Producto p) {
        return fotoPrincipal(p, false);
    }

    /** Hasta 800 px de lado mayor, para vistas grandes. */
    public String imagenPrincipalMediana(Producto p) {
        return fotoPrincipal(p, true);
    }

    private String fotoPrincipal(Producto p, boolean mediana) {
        Presentacion presentacionPrincipal = presentacionService.findByPrincipalAndProductoId(true, p.getId());
        return presentacionPrincipal == null ? null
                : imageService.fotoPresentacion(presentacionPrincipal.getId(), mediana);
    }

    public String imagenPrincipal(Producto p) {
        String id = null;
        Presentacion presentacionPrincipal = presentacionService.findByPrincipalAndProductoId(true, p.getId());
        if(presentacionPrincipal!=null) {
            id = presentacionPrincipal.getId().toString();
        }
        String image =  imageService.getImageWithMediaType(id+".jpg", imageService.imagePresentacionesThumbPath);
        return image;
    }

    public String codigoPrincipal(Producto p){
        Presentacion presentacion = presentacionService.findByPrincipalAndProductoId(true, p.getId());
        if(presentacion!=null){
            if(presentacionResolver.codigoPrincipal(presentacion)!=null){
                return presentacionResolver.codigoPrincipal(presentacion).getCodigo();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public String precioPrincipal(Producto p){
        Presentacion presentacion = presentacionService.findByPrincipalAndProductoId(true, p.getId());
        if(presentacion!=null){
            PrecioPorSucursal precio = presentacionResolver.precioPrincipal(presentacion);
            if(precio!=null){
                return precio.getPrecio().toString();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public CostoPorProducto costo(Producto p){
        return costosPorProductoService.findLastByProductoId(p.getId());
    }

}
