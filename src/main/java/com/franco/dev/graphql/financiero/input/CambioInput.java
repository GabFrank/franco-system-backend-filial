package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class CambioInput {
    private Long id;
    private Double valorEnGs;
    private Boolean activo;
    private Long monedaId;
    private Long usuarioId;
}
