package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class VueltoInput {
    private Long id;
    private Long responsableId;
    private Long autorizadoPorId;
    private Boolean activo;
    private Long usuarioId;
}
