package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.NecesidadEstado;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NecesidadInput {
    private Long id;
    private Long sucursalId;
    private LocalDateTime fecha;
    private NecesidadEstado estado;
    private Long usuarioId;
}
