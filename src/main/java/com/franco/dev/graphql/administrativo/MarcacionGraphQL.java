package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.graphql.administrativo.input.MarcacionInput;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class MarcacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MarcacionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<Marcacion> marcacion(Long id) {
        return service.findById(id);
    }

    public List<Marcacion> marcaciones(Integer page, Integer size) {
        if (page == null)
            page = 0;
        if (size == null)
            size = 10;
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Marcacion> marcacionesPorUsuario(Long usuarioId, String fechaInicio, String fechaFin) {
        if (fechaInicio != null && fechaFin != null) {
            return service.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin);
        }
        return service.findByUsuarioId(usuarioId);
    }

    public Marcacion saveMarcacion(MarcacionInput input) {
        ModelMapper m = new ModelMapper();
        m.getConfiguration().setSkipNullEnabled(true);
        Marcacion e = new Marcacion();

        if (input.getId() != null) {
            Optional<Marcacion> existing = service.findById(input.getId());
            if (existing.isPresent()) {
                e = existing.get();
            }
        }

        m.map(input, e);

        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        if (input.getSucursalEntradaId() != null) {
            e.setSucursalEntrada(sucursalService.findById(input.getSucursalEntradaId()).orElse(null));
        }

        if (input.getSucursalSalidaId() != null) {
            e.setSucursalSalida(sucursalService.findById(input.getSucursalSalidaId()).orElse(null));
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        if (input.getFechaEntrada() != null) {
            e.setFechaEntrada(LocalDateTime.parse(input.getFechaEntrada(), formatter));
        }
        if (input.getFechaSalida() != null) {
            e.setFechaSalida(LocalDateTime.parse(input.getFechaSalida(), formatter));
        }

        return service.save(e);
    }

}
