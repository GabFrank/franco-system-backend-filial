package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.graphql.administrativo.input.HorarioInput;
import com.franco.dev.service.administrativo.HorarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Component
public class HorarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private HorarioService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Horario> horario(Long id) {
        return service.findById(id);
    }

    public List<Horario> horarios(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Horario> horariosPorUsuario(Long usuarioId) {
        return service.findByUsuarioId(usuarioId);
    }

    public Horario saveHorario(HorarioInput input) {
        ModelMapper m = new ModelMapper();
        Horario e = m.map(input, Horario.class);

        if (input.getHoraEntrada() != null)
            e.setHoraEntrada(LocalTime.parse(input.getHoraEntrada()));
        if (input.getHoraSalida() != null)
            e.setHoraSalida(LocalTime.parse(input.getHoraSalida()));
        if (input.getInicioDescanso() != null)
            e.setInicioDescanso(LocalTime.parse(input.getInicioDescanso()));
        if (input.getFinDescanso() != null)
            e.setFinDescanso(LocalTime.parse(input.getFinDescanso()));

        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        if (input.getDias() != null) {
            e.setDias(input.getDias().stream().map(com.franco.dev.domain.administrativo.enums.Dia::valueOf)
                    .collect(java.util.stream.Collectors.toSet()));
        }

        if (input.getTurno() != null) {
            e.setTurno(input.getTurno());
        }

        return service.save(e);
    }

    public Boolean deleteHorario(Long id) {
        return service.deleteById(id);
    }
}
