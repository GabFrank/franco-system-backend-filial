package com.franco.dev.graphql.personas.input;

import lombok.Data;

@Data
public class VendedorProveedorInput {
    private Long id;
    private Long proveedorId;
    private Long vendedorId;
    private Boolean activo;
    private Long usuarioId;
}
