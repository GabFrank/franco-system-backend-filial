package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
public class RetiroDetalleInput {
    private Long id;
    private Double cantidad;
    private Long retiroId;
    private Long monedaId;
    private Double cambio;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
