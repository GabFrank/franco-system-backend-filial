package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.util.Date;

@Data
public class TipoPrecioInput {

    private Long id;
    private String descripcion;
    private Boolean autorizacion;
    private Long usuarioId;
    private Date creadoEn;
    private Boolean activo;
}
