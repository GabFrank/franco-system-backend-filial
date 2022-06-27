package com.franco.dev.graphql.general.input;

import lombok.Data;

@Data
public class PaisInput {
    private Long id;
    private String descripcion;
    private String codigo;
    private Long personaId;
    private Long usuarioId;
}
