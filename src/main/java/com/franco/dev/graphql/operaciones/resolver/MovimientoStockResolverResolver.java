package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MovimientoStockResolverResolver implements GraphQLResolver<MovimientoStock> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MovimientoStockService movimientoStockItemService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private PedidoService pedidoService;

    public Proveedor proveedor(MovimientoStock e){
        if(e.getTipoMovimiento() == TipoMovimiento.COMPRA){
            Pedido pedido = new Pedido();
            pedido = pedidoService.findById(e.getReferencia()).orElse(null);
            if(pedido!=null){
                Proveedor proveedor = new Proveedor();
                proveedor = pedido.getProveedor();
                return proveedor;
            }
        }
    return null;
    }


}
