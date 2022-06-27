package com.franco.dev.graphql.personas.input;

import lombok.Data;

@Data
public class RoleInput {
    private Long id;
    private String nombre;
    private Long usuarioId;
}
