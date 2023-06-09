package com.franco.dev.graphql.personas.input;

import lombok.Data;

@Data
public class ClienteAdicionalInput {
    private Long id;
    private Float credito;
    private Long clienteId;
    private Long personaId;
    private Long usuarioId;
    private String creadoEn;
}
