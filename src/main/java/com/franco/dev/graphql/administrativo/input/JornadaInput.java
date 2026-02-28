package com.franco.dev.graphql.administrativo.input;

import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import lombok.Data;

@Data
public class JornadaInput {
    private Long id;
    private Long sucursalId;
    private Long usuarioId;
    private String fechaInicio;
    private String fechaFin;
    private EstadoJornada estado;
}
