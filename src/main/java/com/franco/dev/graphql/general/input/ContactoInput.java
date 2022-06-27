package com.franco.dev.graphql.general.input;

import lombok.Data;

@Data
public class ContactoInput {
    private Long id;
    private String email;
    private String telefono;
    private Long usuarioId;
}
