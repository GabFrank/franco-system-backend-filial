package com.franco.dev.graphql.personas.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UsuarioInput {
    private Long id;
    private String password;
    private String nickname;
    private LocalDateTime creadoEn;
    private Long personaId;
    private Long usuarioId;
}
