package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class SorteoClienteInput {
    private Long clienteId;
    private String documento;
    private String nombre;
}
