package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.util.Date;

@Data
public class MonedaBilletesInput {
    private Long id;

    private Boolean flotante;
    private Boolean papel;
    private Boolean valor;
    private Boolean activo;

    private Long monedaId;

    private Date creadoEn;

    private Long usuarioId;
}
