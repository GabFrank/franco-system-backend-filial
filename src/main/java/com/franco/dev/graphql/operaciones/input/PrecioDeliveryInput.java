package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class PrecioDeliveryInput {
    private Long id;
    private Double precio;
    private String observacion;
    private Long usuarioId;
}
