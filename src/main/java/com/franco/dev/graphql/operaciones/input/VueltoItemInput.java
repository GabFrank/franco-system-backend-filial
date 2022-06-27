package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class VueltoItemInput {
    private Long id;
    private Long vueltoId;
    private Long monedaId;
    private Double valor;
    private Long usuarioId;
}
