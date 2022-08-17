package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class ConteoMonedaInput implements Serializable {
    private Long id;
    private Long conteoId;
    private Long monedaBilletesId;
    private Double cantidad;
    private String observacion;
    private Date creadoEn;
    private Long usuarioId;
}
