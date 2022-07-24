package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class FacturaLegalItemInput {
    private Long id;
    private String timbrado;
    private String nroSucursal;
    private String nroFactura;
    private Long facturaLegalId;
    private Long ventaItemId;
    private Float cantidad;
    private String descripcion;
    private Double precioUnitario;
    private Double total;
    private Long usuarioId;
}
