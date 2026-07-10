package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.graphql.financiero.input.TerminalPosInput;
import com.franco.dev.service.financiero.CuentaBancariaService;
import com.franco.dev.service.financiero.TerminalPosService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TerminalPosGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TerminalPosService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CuentaBancariaService cuentaBancariaService;

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

    public TerminalPos saveTerminalPos(TerminalPosInput input) {
        ModelMapper m = new ModelMapper();
        TerminalPos e = m.map(input, TerminalPos.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCuentaBancariaId() != null) {
            e.setCuentaBancaria(cuentaBancariaService.findById(input.getCuentaBancariaId()).orElse(null));
        }
        return service.save(e);
    }

    public Boolean deleteTerminalPos(Long id) {
        return service.deleteById(id);
    }
}
