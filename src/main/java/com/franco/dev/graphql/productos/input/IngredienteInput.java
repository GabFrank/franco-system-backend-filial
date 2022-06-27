package com.franco.dev.graphql.productos.input;

import lombok.Data;

@Data
public class IngredienteInput {

    private Long id;
    private String descripcion;
    private String unidadMedida;
    private Long productoId;
    private Long usuarioId;

}
