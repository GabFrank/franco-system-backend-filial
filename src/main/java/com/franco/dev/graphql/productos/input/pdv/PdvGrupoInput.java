package com.franco.dev.graphql.productos.input.pdv;

import lombok.Data;

import java.util.Date;

@Data
public class PdvGrupoInput {

    private Long id;
    private String descripcion;
    private Boolean activo;
    private Long categoriaId;
    private Long usuarioId;
    private Date creadoEn;

}
