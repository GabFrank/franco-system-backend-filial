package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.NecesidadEstado;
import com.franco.dev.domain.operaciones.enums.NecesidadItemEstado;
import lombok.Data;

@Data
public class NecesidadItemInput {

    private Long id;

    private Long necesidadId;

    private Long productoId;

    private Boolean autogenerado;

    private Float cantidadSugerida;

    private Boolean modificado;

    private Float cantidad;

    private String observacion;

    private NecesidadItemEstado estado;

    private Long usuarioId;
}
