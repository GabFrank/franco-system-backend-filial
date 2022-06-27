package com.franco.dev.graphql.configuraciones.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LocalInput {
    private Long id;
    private Boolean isServidor;
    private Long usuarioId;
    private Long sucursalId;
    private LocalDateTime creadoEn;
}
