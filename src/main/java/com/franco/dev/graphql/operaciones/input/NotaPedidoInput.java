package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class NotaPedidoInput {
    private Long id;
    private Long pedidoId;
    private String nota;
    private Long usuarioId;
}
