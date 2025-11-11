package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    @Autowired
    private SucursalService sucursalService;

    public Proveedor proveedor(MovimientoStock e) {
        if (e.getTipoMovimiento() == TipoMovimiento.COMPRA) {
            Pedido pedido = new Pedido();
            pedido = pedidoService.findById(e.getReferencia()).orElse(null);
            if (pedido != null) {
                Proveedor proveedor = new Proveedor();
                proveedor = pedido.getProveedor();
                return proveedor;
            }
        }
        return null;
    }

    public Sucursal sucursal(MovimientoStock e){
        return e.getSucursalId() != null ? sucursalService.findById(e.getSucursalId()).orElse(null) : null;
    }

}
