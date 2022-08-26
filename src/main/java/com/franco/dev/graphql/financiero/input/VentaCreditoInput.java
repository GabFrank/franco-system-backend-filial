package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.financiero.enums.TipoConfirmacion;
import lombok.Data;

@Data
public class VentaCreditoInput {
    private Long id;
    private Long sucursalId;
    private Long ventaId;
    private Long clienteId;
    private TipoConfirmacion tipoConfirmacion;
    private Integer cantidadCuotas;
    private Double valorTotal;
    private Double saldoTotal;
    private Integer plazoEnDias;
    private Integer interesPorDia;
    private Integer interesMoraPorDia;
    private EstadoVentaCredito estado;
    private Long usuarioId;
}
