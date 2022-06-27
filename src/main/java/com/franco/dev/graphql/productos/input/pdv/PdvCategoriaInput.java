package com.franco.dev.graphql.productos.input.pdv;

import lombok.Data;

import java.util.Date;

@Data
public class PdvCategoriaInput {

    private Long id;
    private String descripcion;
    private Boolean activo;
    private Long usuarioId;
    private Date creadoEn;
    private Integer posicion;

}
