package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.service.financiero.TerminalPosService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * TerminalPos en filial es de solo lectura: los registros llegan unicamente
 * por replicacion logica desde el central (direction MAIN_TO_ALL). Filial
 * nunca crea ni elimina terminales localmente.
 */
@Component
public class TerminalPosGraphQL implements GraphQLQueryResolver {

    @Autowired
    private TerminalPosService service;

    public Optional<TerminalPos> terminalPos(Long id) {
        return service.findById(id);
    }

    public List<TerminalPos> terminalesPos(int page, int size) {
        return service.findAll2();
    }

    public List<TerminalPos> searchTerminalPos(String texto) {
        return service.searchByAll(texto);
    }

    public Page<TerminalPos> filterTerminalPos(String descripcion, String codigo, Boolean activo, int page, int size) {
        return service.filter(descripcion, codigo, activo, page, size);
    }

    public Long countTerminalPos() {
        return service.count();
    }
}
