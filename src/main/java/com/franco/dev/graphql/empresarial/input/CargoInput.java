package com.franco.dev.graphql.empresarial.input;

import lombok.Data;

@Data
public class CargoInput {
    private Long id;
    private String nombre;
    private String descripcion;
    private Long supervisadoPorId;
    private Long sueldoBase;
    private Long usuarioId;
}
