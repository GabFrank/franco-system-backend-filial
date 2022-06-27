package com.franco.dev.graphql.productos.input;

import lombok.Data;

@Data
public class ProductoIngredienteInput {
    private Long id;
    private Long productoId;
    private Long ingredienteId;
    private Float cantidad;
    private Boolean extra;
    private Float precio;
    private Boolean adicional;
    private Long usuarioId;
}
