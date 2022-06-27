package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PrecioPorSucursalInput {
    private Long id;
    private Long presentacionId;
    private Long tipoPrecioId;
    private Long sucursalId;
    private Float precio;
    private LocalDateTime creadoEn;
    private Long usuarioId;
    private Boolean activo;
    private Boolean principal;
}
