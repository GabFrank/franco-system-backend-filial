package com.franco.dev.graphql.personas.input;

import lombok.Data;

@Data
public class ClienteUpdateInput {
    private Long id;
    private Float credito;
    private Long personaId;
}
