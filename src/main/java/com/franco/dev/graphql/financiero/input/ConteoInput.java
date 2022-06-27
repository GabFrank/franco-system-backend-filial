package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.util.Date;

@Data
public class ConteoInput {
    private Long id;

    private String observacion;

    private Date creadoEn;

    private Long usuarioId;

    private Double totalGs;
    private Double totalRs;
    private Double totalDs;

}
