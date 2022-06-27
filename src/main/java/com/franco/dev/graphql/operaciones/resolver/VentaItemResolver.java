package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.enums.UnidadMedida;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VentaItemResolver implements GraphQLResolver<VentaItem> {

    @Autowired
    private VentaItemService ventaItemService;

    public Double valorTotal(VentaItem v){
        Integer cantidad = 1;
        if(v.getUnidadMedida() == UnidadMedida.CAJA){
            cantidad = v.getProducto().getUnidadPorCaja();
        }
        return (v.getPrecioVenta().getPrecio() * v.getCantidad() * cantidad);
    }
}
