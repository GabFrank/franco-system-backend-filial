package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VentaCreditoResolver implements GraphQLResolver<VentaCredito> {

    @Autowired
    private SucursalService sucursalService;

    public Sucursal sucursal(VentaCredito ventaCredito) {
        if (ventaCredito.getSucursalId() != null) {
            return sucursalService.findById(ventaCredito.getSucursalId()).orElse(null);
        }
        return null;
    }
}
