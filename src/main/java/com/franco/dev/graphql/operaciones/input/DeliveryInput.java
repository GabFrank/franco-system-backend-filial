package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import lombok.Data;

@Data
public class DeliveryInput {
    private Long id;
    private Long ventaId;
    private Long funcionarioId;
    private Long vehiculoId;
    private String direccion;
    private String telefono;
    private DeliveryEstado estado;
    private Long precioId;
    private Long usuarioId;
    private Double valor;
    private Long barrioId;
    private Long vueltoId;
}
