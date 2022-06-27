package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class BancoInput {
    private Long id;

    private String nombre;

    private String codigo;

    private Long usuarioId;
}
