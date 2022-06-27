package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.VentaEstado;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CobroInput {
    private Long id;
    private LocalDateTime creadoEn;
    private Long usuarioId;
    private Double totalGs;
}
