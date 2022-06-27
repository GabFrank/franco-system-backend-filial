package com.franco.dev.graphql.personas.input;

import lombok.Data;

@Data
public class ProveedorInput {
    private Long id;
    private Boolean credito;
    private String tipoCredito;
    private Integer chequeDias;
    private Long datosBancariosId;
    private Long personaId;
    private Long usuarioId;
}
