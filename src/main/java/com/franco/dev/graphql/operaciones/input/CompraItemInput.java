package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CompraItemInput {
    private Long id;
    private Long productoId;
    private Long compraId;
    private Float cantidad;
    private Double precioUnitario;
    private Double descuentoUnitario;
    private Double valorTotal;
    private Boolean bonificacion;
    private String observacion;
    private String lote;
    private LocalDateTime vencimiento;
    private Long usuarioId;
    private CompraItemEstado estado;
    private LocalDateTime creadoEn;
    private Long pedidoItemId;
    private Long presentacionId;
    private Boolean verificado;
    private Long programarPrecioId;
}
