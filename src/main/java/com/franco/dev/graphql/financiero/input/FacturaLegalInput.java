package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class FacturaLegalInput implements Serializable {
    private Long id;
    private Long cajaId;
    private Long timbradoDetalleId;
    private Boolean autoimpreso;
    private Boolean viaTributaria;
    private Integer numeroFactura;
    private Long clienteId;
    private Long ventaId;
    private LocalDateTime fecha;
    private Boolean credito;
    private String nombre;
    private String ruc;
    private String direccion;
    private Double ivaParcial0;
    private Double ivaParcial5;
    private Double ivaParcial10;
    private Double totalParcial0;
    private Double totalParcial5;
    private Double totalParcial10;
    private Double totalFinal;
    private Long usuarioId;
}
