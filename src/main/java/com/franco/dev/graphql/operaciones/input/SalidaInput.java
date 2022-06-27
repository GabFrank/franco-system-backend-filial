package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.TipoEntrada;
import com.franco.dev.domain.operaciones.enums.TipoSalida;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalidaInput {
    private Long id;
    private Long responsableCargaId;
    private Long sucursalId;
    private String observacion;
    private TipoSalida tipoSalida;
    private Boolean activo;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
