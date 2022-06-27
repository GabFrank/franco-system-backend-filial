package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.util.Date;

@Data
public class TipoPresentacionInput {

    private Long id;
    private String descripcion;
    private Boolean unico;
    private Long usuarioId;
    private Date creadoEn;
}
