package com.franco.dev.graphql.financiero.input;

import lombok.Data;

/**
 * Lo que el PDV manda para imprimir la seña de un cobro con tarjeta sin cupón.
 *
 * Todos los campos son opcionales salvo `qr`: si no hay QR no hay seña — el papel existe
 * justamente para poder escanearlo después. El resto es el respaldo legible, y un dato que
 * falte deja un renglón menos, no un error.
 */
@Data
public class SenaCuponInput {
    private Long ventaId;
    private Long ventaTarjetaId;
    private Long cajaId;
    private String cajero;
    private String terminal;
    private Double monto;
    private String monedaSimbolo;
    private Integer decimales;
    private String qr;
}
