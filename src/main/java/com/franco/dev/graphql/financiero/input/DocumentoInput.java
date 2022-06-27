package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentoInput {
    private Long id;
    private String descripcion;
    private Boolean activo;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
