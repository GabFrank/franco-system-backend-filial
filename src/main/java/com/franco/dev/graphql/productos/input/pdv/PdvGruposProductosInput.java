package com.franco.dev.graphql.productos.input.pdv;

import lombok.Data;

import java.util.Date;

@Data
public class PdvGruposProductosInput {

    private Long id;
    private Boolean activo;
    private Long grupoId;
    private Long productoId;
    private Long usuarioId;
    private Date creadoEn;

}
