package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.util.Date;

@Data
public class FamiliaInput {

    private Long id;
    private String nombre;
    private String descripcion;
    private Boolean activo;
    private Long usuarioId;
    private Date creadoEn;
    private String icono;
    private Integer posicion;

}
