package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.enums.UnidadMedida;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class VentaResolver implements GraphQLResolver<Venta> {

    @Autowired
    private VentaService service;

    @Autowired
    private VentaItemService ventaItemService;

    @Autowired
    private DeliveryService deliveryService;

    public List<VentaItem> ventaItemList(Venta v){
        return ventaItemService.findByVentaId(v.getId());
    }

    public Double valorDescuento(Venta v){
        Double descuento = 0.0;
        List<VentaItem> ventaItemList = ventaItemService.findByVentaId(v.getId());
        for (VentaItem vi : ventaItemList){
            if(vi.getValorDescuento()!=null){
                descuento += vi.getValorDescuento() * vi.getCantidad();
            }
        }
        return descuento;
    }

    public Double valorTotal(Venta v){
        Double precio = 0.0;
        Integer cantidad = 1;
        List<VentaItem> ventaItemList = ventaItemService.findByVentaId(v.getId());
        for (VentaItem vi : ventaItemList){
            if(vi.getUnidadMedida() == UnidadMedida.CAJA){
                cantidad = vi.getProducto().getUnidadPorCaja();
            }
            try {
                precio += vi.getPrecioVenta().getPrecio() * (vi.getCantidad() * cantidad);
            } catch (Exception e){

            }
            cantidad = 1;
        }
        return precio;
    }

    public Delivery delivery(Venta v){
        return deliveryService.findByVentaId(v.getId(), v.getSucursalId());
    }
}
