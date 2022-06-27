package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ComboItemInput {
    private Long id;
    private Long productoId;
    private Long comboId;
    private Long intercambiablePorProductoId;
    private Float cantidad;
    private Long usuarioId;
}
