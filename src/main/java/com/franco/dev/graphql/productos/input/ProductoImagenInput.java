package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.util.Date;

@Data
public class ProductoImagenInput {

    private Long id;
    private String ruta;
    private Long productoId;
    private Boolean principal;
    private Long usuarioId;
    private Date creadoEn;

}
