package com.franco.dev.graphql.empresarial.input;

import lombok.Data;

@Data
public class ConfiguracionGeneralInput {
    private Long id;
    private String nombreEmpresa;

    private String razonSocial;

    private String ruc;
    private Long usuarioId;

}
