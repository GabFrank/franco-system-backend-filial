package com.franco.dev.graphql.empresarial.resolver;

import com.franco.dev.domain.empresarial.Sector;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.SectorService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SucursalResolver implements GraphQLResolver<Sucursal> {

    @Autowired
    private SectorService sectorService;

    public List<Sector> sectorList(Sucursal e) {
        return sectorService.findBySucursalId(e.getId());
    }

}
