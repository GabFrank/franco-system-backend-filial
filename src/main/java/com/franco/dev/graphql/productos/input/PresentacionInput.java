package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.util.Date;

@Data
public class PresentacionInput {

    private Long id;
    private String descripcion;
    private Boolean principal;
    private Double cantidad;
    private Long productoId;
    private Long tipoPresentacionId;
    private Long usuarioId;
    private Date creadoEn;
    private Boolean activo;
}
