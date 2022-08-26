package com.franco.dev.graphql.personas.input;

import com.franco.dev.domain.personas.enums.TipoCliente;
import lombok.Data;

@Data
public class ClienteInput {
    private Long id;
    private TipoCliente tipoCliente;
    private Float credito;
    private String codigo;
    private Long sucursalId;
    private Long personaId;
    private Long usuarioId;
    private String nombre;
    private String documento;
    private String direccion;
}
