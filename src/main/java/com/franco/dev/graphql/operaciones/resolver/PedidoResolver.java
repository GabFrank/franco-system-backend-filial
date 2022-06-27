package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import com.franco.dev.service.operaciones.CompraService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoItemSucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.SubFamiliaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PedidoResolver implements GraphQLResolver<Pedido> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private PedidoItemSucursalService pedidoItemSucursalService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Autowired
    private CompraService compraService;

    public Optional<Usuario> usuario(Pedido e){
        return usuarioService.findById(e.getUsuario().getId());
    }

    public List<PedidoItem> pedidoItens(Pedido e) { return pedidoItemService.findByPedidoId(e.getId());}

    public Double valorTotal(Pedido e) {
        List<PedidoItem> pedidoItemList = pedidoItemService.findByPedidoId(e.getId());
        Double total = 0.0;
        for(PedidoItem p: pedidoItemList){
            total += p.getCantidad() * (p.getPrecioUnitario() - p.getDescuentoUnitario());
        }
        total -= e.getDescuento();
        return total;
    }

    public String nombreProveedor(Pedido e){
        return e.getProveedor().getPersona().getNombre();
    }

    public String nombreUsuario(Pedido e){
        return e.getUsuario().getPersona().getNombre();
    }

//    public TipoConservacion tipoConservacion(Pedido e){ return e.getTipoConservacion(); }
//    private Optional<SubFamilia> subFamilia(Pedido e) {
//        return subFamiliaService.findById(e.getSubFamilia().getId());
//    }

    public List<NotaRecepcion> notaRecepcionList(Pedido e){
        return notaRecepcionService.findByPedidoId(e.getId());
    }

    public Compra compra(Pedido e){
        return compraService.findByPedidoId(e.getId());
    }

}
