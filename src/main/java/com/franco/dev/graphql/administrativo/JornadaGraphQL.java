package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JornadaGraphQL implements GraphQLQueryResolver {

    @Autowired
    private JornadaService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Jornada> jornada(Long id, Long sucursalId) {
        return service.findByIdAndSucursalId(id, sucursalId);
    }

    public List<Jornada> jornadas(String fechaInicio, String fechaFin, Integer page, Integer size) {
        if (fechaInicio != null && fechaFin != null) {
            return service.findByFechaRange(fechaInicio, fechaFin);
        }
        if (page == null)
            page = 0;
        if (size == null)
            size = 10;
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Jornada> jornadasPorUsuario(Long usuarioId, String fechaInicio, String fechaFin) {
        if (fechaInicio != null && fechaFin != null) {
            return service.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin);
        }
        return service.findByUsuarioId(usuarioId);
    }
}
