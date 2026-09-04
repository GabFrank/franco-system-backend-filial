package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FormatoQrPos;
import com.franco.dev.service.financiero.FormatoQrPosService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Solo queries: el formato se administra en el central (MAIN_TO_ALL). Ver
 * {@link com.franco.dev.domain.financiero.FormatoQrPos}.
 */
@Component
public class FormatoQrPosGraphQL implements GraphQLQueryResolver {

    @Autowired
    private FormatoQrPosService service;

    public List<FormatoQrPos> formatosQrPosActivos() {
        return service.findActivos();
    }
}
