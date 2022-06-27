package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.TipoCuenta;
import lombok.Data;

@Data
public class CuentaBancariaInput {
    private Long id;
    private Long personaId;
    private Long bancoId;
    private Long monedaId;
    private String numbero;
    private TipoCuenta tipoCuenta;
    private Long usuarioId;
}
