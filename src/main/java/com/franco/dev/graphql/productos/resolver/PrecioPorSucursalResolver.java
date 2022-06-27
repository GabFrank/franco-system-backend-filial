package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PrecioPorSucursalResolver implements GraphQLResolver<PrecioPorSucursal> {

    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;

//    public List<PrecioPorSucursal> precioPorSucursalPorProductoId(PrecioPorSucursal c){
//
//        return precioPorSucursalService.find(c.getId());
//    }

}
