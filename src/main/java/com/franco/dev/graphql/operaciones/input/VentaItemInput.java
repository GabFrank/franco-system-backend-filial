package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VentaItemInput {
    private Long id;
    private Long ventaId;
    private Long productoId;
    private String productoDescripcion;
    private Long presentacionId;
    private String presentacionDescripcion;
    private Double cantidad;
    private Double existencia;
    private Double precioCosto;
    private Long precioVentaId;
    private Double precioVenta;
    private Double valorDescuento;
    private LocalDateTime creadoEn;
    private Long usuarioId;
    private Boolean activo;
}
