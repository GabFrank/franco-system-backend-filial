package com.franco.dev.graphql.general.input;

import lombok.Data;

@Data
public class BarrioInput {
    private Long id;
    private String descripcion;
    private Long ciudadId;
    private Long precioDeliveryId;
    private Long usuarioId;
}
