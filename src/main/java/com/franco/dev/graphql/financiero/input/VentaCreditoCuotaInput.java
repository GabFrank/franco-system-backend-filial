package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class VentaCreditoCuotaInput {
    private Long id;
    private Long ventaCreditoId;
    private Long cobroId;
    private Double valor;
    private Boolean parcial;
    private Boolean activo;
    private String vencimiento;
    private Long usuarioId;
}
