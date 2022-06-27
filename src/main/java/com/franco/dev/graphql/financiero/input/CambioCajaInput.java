package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.util.Date;

@Data
public class CambioCajaInput {
    private Long id;

    private Long clienteId;

    private Long autorizadoPorId;

    private Long monedaVentaId;

    private Long monedaCompraId;

    private Double cotizacion;

    private String observacion;

    private Date creadoEn;

    private Long usuarioId;
}
