package com.franco.dev.graphql.configuraciones.input;

import com.franco.dev.domain.configuracion.enums.NivelActualizacion;
import com.franco.dev.domain.configuracion.enums.TipoActualizacion;
import lombok.Data;

@Data
public class ActualizacionInput {
    private Long id;
    private String currentVersion;
    private Boolean enabled;
    private TipoActualizacion tipo;
    private NivelActualizacion nivel;
    private String title;
    private String msg;
    private String btn;
    private Long usuarioId;
}
