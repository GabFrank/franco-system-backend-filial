package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.NecesidadEstado;
import com.franco.dev.domain.operaciones.enums.TipoTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TransferenciaInput {
    private Long id;
    private Long sucursalOrigenId;
    private Long sucursalDestinoId;
    private LocalDateTime creadoEn;
    private TransferenciaEstado estado;
    private TipoTransferencia tipo;
    private EtapaTransferencia etapa;
    private Boolean isOrigen;
    private Boolean isDestino;
    private String observacion;
    private Long usuarioPreTransferenciaId;
    private Long usuarioPreparacionId;
    private Long usuarioTransporteId;
    private Long usuarioRecepcionId;}
