package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.service.productos.CostosPorProductoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CostoPorProductoResolver implements GraphQLResolver<CostoPorProducto> {

    @Autowired
    private CostosPorProductoService costosPorProductoService;

//    public List<PrecioPorSucursal> precioPorSucursalPorProductoId(PrecioPorSucursal c){
//
//        return precioPorSucursalService.find(c.getId());
//    }

}
