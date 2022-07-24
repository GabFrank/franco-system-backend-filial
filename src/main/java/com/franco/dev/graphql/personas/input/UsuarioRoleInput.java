package com.franco.dev.graphql.personas.input;

import lombok.Data;

@Data
public class UsuarioRoleInput {
    private Long id;
    private Long userId;
    private Long roleId;
    private Long usuarioId;
}
