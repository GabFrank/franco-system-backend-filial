package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.service.financiero.FormatoTerminalPosService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Solo queries: el formato se administra en el central (MAIN_TO_ALL). Ver
 * {@link com.franco.dev.domain.financiero.FormatoTerminalPos}.
 */
@Component
public class FormatoTerminalPosGraphQL implements GraphQLQueryResolver {

    @Autowired
    private FormatoTerminalPosService service;

    public List<FormatoTerminalPos> formatosTerminalPosActivos() {
        return service.findActivos();
    }
}
