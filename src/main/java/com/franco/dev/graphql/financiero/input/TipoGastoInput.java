package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.TipoNaturalezaGasto;
import lombok.Data;
import java.util.Date;

@Data
public class TipoGastoInput {
    private Long id;
    private Boolean isClasificacion;
    private Boolean activo;
    private Boolean activoEnSucursales;
    private Boolean autorizacion;
    private String descripcion;
    private String simbolo;
    private Long clasificacionGastoId;
    private Long cargoId;
    private Date creadoEn;
    private Long usuarioId;
    private TipoNaturalezaGasto tipoNaturaleza;
}
