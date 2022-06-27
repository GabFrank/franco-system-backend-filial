package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class MotivoDiferenciaPedidoInput {
    private Long id;
    private String tipo;
    private String descripcion;
    private Long usuarioId;
}
