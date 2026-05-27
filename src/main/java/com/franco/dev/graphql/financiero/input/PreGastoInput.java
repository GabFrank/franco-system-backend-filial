package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import lombok.Data;
import java.util.Date;

@Data
public class PreGastoInput {
    private Long id;
    private Long sucursalId;
    private Long funcionarioId;
    private Long enteId;
    private Long tipoGastoId;
    private String descripcion;
    private Long monedaId;
    private Double montoSolicitado;
    private Long sucursalCajaId;
    private Long cajaId;
    private EstadoPreGasto estado;
    private Long autorizadoPorId;
    private Long delegadoAId;
    private String motivoRechazo;
    private Double montoRetirado;
    private Long usuarioId;
    private Date creadoEn;
}
