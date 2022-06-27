package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import lombok.Data;

import java.util.Date;

@Data
public class RetiroInput {
    private Long id;

    private EstadoRetiro estado;

    private Long responsableId;

    private Date creadoEn;

    private Long usuarioId;

    private Long cajaSalidaId;

    private Long cajaEntradaId;

    private Double retiroGs;

    private Double retiroRs;

    private Double retiroDs;
}
