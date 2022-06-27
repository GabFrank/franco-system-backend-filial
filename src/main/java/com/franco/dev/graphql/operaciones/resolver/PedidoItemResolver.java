package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoItemSucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.operaciones.CompraItemService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoItemSucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PedidoItemResolver implements GraphQLResolver<PedidoItem> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private PedidoItemSucursalService pedidoItemSucursalService;

    @Autowired
    private CompraItemService compraItemService;

    public List<PedidoItemSucursal> pedidoItemSucursales(PedidoItem e){
        return pedidoItemSucursalService.findByPedidoItemId(e.getId());
    }

    public Double valorTotal(PedidoItem e) {
        Double parcial = 0.0;
        return parcial + ((e.getPrecioUnitario() - e.getDescuentoUnitario()) * e.getCantidad());
    }

    public CompraItem compraItem(PedidoItem e){
        return compraItemService.findByPedidoItemId(e.getId());
    }

//    public TipoConservacion tipoConservacion(Pedido e){ return e.getTipoConservacion(); }
//    private Optional<SubFamilia> subFamilia(Pedido e) {
//        return subFamiliaService.findById(e.getSubFamilia().getId());
//    }

}
