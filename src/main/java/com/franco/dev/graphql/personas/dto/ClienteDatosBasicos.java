package com.franco.dev.graphql.personas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteDatosBasicos {
    private String ruc;
    private String razonSocial;
    private String direccion;
    private String estado;
    private String estadoContribuyente;
    private Boolean tributa;
    private Integer tipoContribuyente;
    private String telefono;
    private String nombreFantasia;
    private String dv;
}

