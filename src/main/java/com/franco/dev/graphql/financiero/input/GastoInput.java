package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.util.Date;

@Data
public class GastoInput {
    private Long id;

    private Long responsableId;

    private Long cajaId;

    private Long tipoGastoId;

    private Long autorizadoPorId;

    private String observacion;

    private Date creadoEn;

    private Long usuarioId;

    private Double vueltoGs;

    private Double vueltoRs;

    private Double vueltoDs;

    private Boolean activo;

    private Boolean finalizado;

    private Double retiroGs;
    private Double retiroRs;
    private Double retiroDs;


}
