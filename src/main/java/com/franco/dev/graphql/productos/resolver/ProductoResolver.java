package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.*;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import com.franco.dev.graphql.productos.ProductoExistenciaCostoGraphQL;
import com.franco.dev.service.empresarial.SucursalService;
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

import java.time.LocalDateTime;
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
    private CostosPorSucursalService costosPorSucursalService;

    @Autowired
    private CodigoService codigoService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private ProductoPorSucursalService productoPorSucursalService;

    @Autowired
    private ProductoImagenService productoImagenService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PresentacionResolver presentacionResolver;

    private ProductoExistenciaCostoGraphQL productoExistenciaCostoGraphQL;

    public Usuario usuario(Producto e){
        if(e.getUsuario()!=null) {
            return usuarioService.findById(e.getUsuario().getId()).orElse(null);
        } else {
            return null;
        }
    }

    private LocalDateTime fechaUltimaCompra;

    public TipoConservacion tipoConservacion(Producto e){ return e.getTipoConservacion(); }

    public List<ProductoIngrediente> ingredientesList(Producto p){
        List<Ingrediente> ingredienteList = new ArrayDeque<>();
        List<ProductoIngrediente> productoIngredienteList = productoIngredienteService.findByProducto(p.getId());
        for(ProductoIngrediente pi : productoIngredienteList){
            ingredienteList.add(ingredienteService.findById(pi.getIngrediente().getId()).orElse(null));
        }
        return productoIngredienteList;
    }

    public List<ExistenciaCostoPorSucursal> sucursales(Producto p) {
        List<ExistenciaCostoPorSucursal> epsList = new ArrayList<>();
        List<Sucursal> sucursalList = sucursalService.findAll2();
        for (Sucursal s : sucursalList ){
            ExistenciaCostoPorSucursal eps = new ExistenciaCostoPorSucursal();
            eps.setExistencia(movimientoStockService.stockByProductoIdAndSucursalId(p.getId(), s.getId()));
            eps.setSucursal(s);
            CostoPorProducto cps = costosPorSucursalService.findLastByProductoId(p.getId());
            MovimientoStock ms = null;
            if(cps!=null){
                if(cps.getMovimientoStock()!=null){
                    eps.setCantidadUltimaCompra(cps.getMovimientoStock().getCantidad());
                }
                ms = cps.getMovimientoStock();
                eps.setPrecioUltimaCompra(cps.getUltimoPrecioCompra());
                eps.setCostoMedio(cps.getCostoMedio());
                eps.setFechaUltimaCompra(cps.getCreadoEn());
                eps.setMoneda(cps.getMoneda());
            }

            Long pedidoId;
            Pedido pedido;
            if(ms!=null){
                if(ms.getTipoMovimiento()==TipoMovimiento.COMPRA){
                    pedidoId = ms.getReferencia();
                    pedido = pedidoService.findById(pedidoId).orElse(null);
                    if(pedido!=null){
                        eps.setPedido(pedido);
                    }
                }

            }
            if(eps.getPrecioUltimaCompra()==null){
                eps.setPrecioUltimaCompra(Double.parseDouble("0"));
            }
            if(eps.getCantidadUltimaCompra()==null){
                eps.setCantidadUltimaCompra(Double.parseDouble("0"));
            }
            if(eps.getCostoMedio()==null){
                eps.setCostoMedio(Double.parseDouble("0"));
            }

            //adicionar cantidades minimas, maximas y medias
            ProductoPorSucursal pps = productoPorSucursalService.findByProIdSucId(p.getId(), s.getId());
            if(pps!=null){
                eps.setCantMaxima(pps.getCantMaxima());
                eps.setCantMedia(pps.getCantMedia());
                eps.setCantMinima(pps.getCantMinima());
            }

            epsList.add(eps);
        }
        return epsList;
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
            CostoPorProducto cps = costosPorSucursalService.findByMovimientoStockId(ms.getId());
            if(cps!=null){
                pc.setPrecio(cps.getUltimoPrecioCompra());
            }
            pcList.add(pc);
        }
        return pcList;
    }

    public List<Presentacion> presentaciones(Producto p){
        return presentacionService.findByProductoId(p.getId());
    }

    public String imagenPrincipal(Producto p) {
        String id = null;
        Presentacion presentacionPrincipal = presentacionService.findByPrincipalAndProductoId(true, p.getId());
        if(presentacionPrincipal!=null) {
            id = presentacionPrincipal.getId().toString();
        }
        String image =  imageService.getImageWithMediaType("productos/presentaciones/"+id+".jpg");
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
            return presentacionResolver.precioPrincipal(presentacion).getPrecio().toString();
        } else {
            return null;
        }
    }

    public CostoPorProducto costo(Producto p){
        return costosPorSucursalService.findLastByProductoId(p.getId());
    }

}
