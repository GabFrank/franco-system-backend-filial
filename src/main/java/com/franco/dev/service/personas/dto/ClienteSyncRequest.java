package com.franco.dev.service.personas.dto;

import com.franco.dev.domain.personas.enums.TipoCliente;
import lombok.Data;

@Data
public class ClienteSyncRequest {

    private Long id;
    private TipoCliente tipo;
    private Float credito;
    private String codigo;
    private Long sucursalId;
    private Long personaId;
    private Long usuarioId;
    private Boolean tributa;
    private Boolean verificadoSet;
}

