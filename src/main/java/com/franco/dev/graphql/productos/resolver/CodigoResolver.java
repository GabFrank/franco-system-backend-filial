package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Combo;
import com.franco.dev.domain.productos.ComboItem;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.service.productos.ComboItemService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.productos.TipoPrecioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CodigoResolver implements GraphQLResolver<Codigo> {

    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;

    @Autowired
    private TipoPrecioService tipoPrecioService;

}
