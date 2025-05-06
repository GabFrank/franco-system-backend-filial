package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.service.operaciones.VentaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeliveryResolver implements GraphQLResolver<Delivery> {

    @Autowired
    private VentaService ventaService;

    public Venta venta(Delivery e) {
        return ventaService.getRepository().findByDeliveryIdAndSucursalId(e.getId(), e.getSucursalId());
    }
}
