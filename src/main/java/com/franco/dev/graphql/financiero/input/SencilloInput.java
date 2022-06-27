package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.util.Date;

@Data
public class SencilloInput {
    private Long id;

    private Long responsableId;

    private Long autorizadoPorId;

    private Boolean entrada;

    private Date creadoEn;

    private Long usuarioId;
}
