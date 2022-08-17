package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.io.Serializable;

@Data
public class FacturaLegalItemInput implements Serializable {
    private Long id;
    private Long clienteId;
    private Long facturaLegalId;
    private Long ventaItemId;
    private Double cantidad;
    private String descripcion;
    private Double precioUnitario;
    private Integer iva;
    private Double total;
    private Long usuarioId;
}
