package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    private Double precio;
    private Double precioVenta;
    private Double valorDescuento;
    private LocalDateTime creadoEn;
    private Long usuarioId;
    private Boolean activo;
    private Long sucursalId;
    /** Rastro de la conversion del precio de lista (V110.5); null = sin conversion. */
    private Long precioMonedaId;
    private Double precioLista;
    private Double precioCotizacion;
    /** Precio unitario en la moneda de la venta (V111.5); precio va siempre en la de la empresa. */
    private Double precioEnMoneda;
    /** Lotes elegidos a mano por el cajero. Null o vacío = FEFO puro. */
    private List<VentaItemLoteInput> lotes;
}
