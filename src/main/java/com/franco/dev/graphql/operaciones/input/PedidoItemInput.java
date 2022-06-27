package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PedidoItemInput {
    private Long id;
    private Long productoId;
    private Long pedidoId;
    private Double precioUnitario;
    private Double descuentoUnitario;
    private Double valorTotal;
    private Boolean bonificacion;
    private String bonificacionDetalle;
    private String observacion;
    private LocalDateTime vencimiento;
    private Boolean frio;
    private Long usuarioId;
    private PedidoItemEstado estado;
    private Long presentacionId;
    private Double cantidad;
    private LocalDateTime creadoEn;
    private Long notaRecepcionId;
}
