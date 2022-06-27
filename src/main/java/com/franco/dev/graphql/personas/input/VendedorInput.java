package com.franco.dev.graphql.personas.input;

import lombok.Data;

@Data
public class VendedorInput {
    private Long id;
    private String descripcion;
    private Long personaId;
    private Long usuarioId;
}
