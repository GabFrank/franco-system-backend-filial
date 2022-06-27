package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class MonedaInput {
    private Long id;
    private String denominacion;
    private String simbolo;
    private Long paisId;
    private Long usuarioId;
}
