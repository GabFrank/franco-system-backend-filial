package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CodigoInput {
    private Long id;
    private String codigo;
    private Boolean principal;
    private Boolean activo;
    private Long presentacionId;
    private Long usuarioId;
}
