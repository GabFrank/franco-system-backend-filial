package com.franco.dev.graphql.general.input;

import lombok.Data;

@Data
public class CiudadInput {
    private Long id;
    private String descripcion;
    private String codigo;
    private Long paisId;
    private Long usuarioId;
}
