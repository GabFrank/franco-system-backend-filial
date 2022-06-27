package com.franco.dev.graphql.personas.input;

import lombok.Data;

@Data
public class ClienteInput {
    private Long id;
    private Float credito;
    private Long sucursalId;
    private Long personaId;
    private Long usuarioId;
}
