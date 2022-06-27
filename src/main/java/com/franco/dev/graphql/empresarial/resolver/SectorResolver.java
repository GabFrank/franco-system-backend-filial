package com.franco.dev.graphql.empresarial.resolver;

import com.franco.dev.domain.empresarial.Sector;
import com.franco.dev.domain.empresarial.Zona;
import com.franco.dev.service.empresarial.ZonaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SectorResolver implements GraphQLResolver<Sector> {

    @Autowired
    private ZonaService zonaService;

    public List<Zona> zonaList(Sector e) {
        return zonaService.findBySectorId(e.getId());
    }

}
