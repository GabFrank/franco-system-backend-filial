package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VentaTarjetaInput {
    private Long id;
    private Long sucursalId;
    private Long ventaId;
    private Long terminalPosId;

    /** Moneda del cobro que este registro respalda. Sin ella, monto no tiene unidad. */
    private Long monedaId;
    private Long cajaId;
    private String codigoAutorizacion;
    private String numeroBoleta;
    private BigDecimal monto;
    private BigDecimal montoEscaneado;
    private String imagenUrl;
    private Long usuarioId;
    private String estado;
}
