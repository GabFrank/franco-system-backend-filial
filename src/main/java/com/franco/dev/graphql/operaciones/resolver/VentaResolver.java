package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.enums.UnidadMedida;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.sorteo.SorteoParticipacionService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
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

    @Autowired
    private SorteoParticipacionService sorteoParticipacionService;

    /** Una sola consulta por venta; false ante cualquier error (nunca rompe el listado). */
    public Boolean tieneSorteo(Venta v) {
        if (v == null || v.getId() == null) return false;
        return sorteoParticipacionService.tieneParticipaciones(v.getId());
    }

    public List<VentaItem> ventaItemList(Venta v){
        return ventaItemService.findByVentaId(v.getId());
    }

    public List<VentaObservacion> ventaObservacionList(Venta v){
        return Collections.emptyList();
    }

    /** Se suma en la base: cargar los items solo para esto traia producto, presentacion y precio de cada uno. */
    public Double valorDescuento(Venta v){
        if (v == null || v.getId() == null) return 0.0;
        return ventaItemService.totalDescuentoByVentaId(v.getId());
    }

    public Double valorTotal(Venta v){
        Double precio = 0.0;
//        Integer cantidad = 1;
//        List<VentaItem> ventaItemList = ventaItemService.findByVentaId(v.getId(), v.getSucursalId());
//        for (VentaItem vi : ventaItemList){
//            if(vi.getUnidadMedida() == UnidadMedida.CAJA){
//                cantidad = vi.getProducto().getUnidadPorCaja();
//            }
//            precio += vi.getPrecioVenta().getPrecio() * (vi.getCantidad() * cantidad);
//            cantidad = 1;
//        }
//        return precio;
        precio = ventaItemService.getRepository().totalByVentaIdAndSucId(v.getId(), v.getSucursalId());
        if(precio != null) {
            return precio;
        } else {
            return 0.0;
        }
    }
}
