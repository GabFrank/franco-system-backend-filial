package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class FormaPagoInput {

    private Long id;
    private String descripcion;
    private Boolean activo;
    private Boolean movimientoCaja;
    private Long cuentaBancariaId;
    private Boolean autorizacion;
    private Long usuarioId;

}
