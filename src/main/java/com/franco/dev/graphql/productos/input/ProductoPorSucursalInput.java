package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductoPorSucursalInput {
    private Long id;
    private Long productoId;
    private Long sucursalId;
    private Float cantMinima;
    private Float cantMaxima;
    private Float cantMedia;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
