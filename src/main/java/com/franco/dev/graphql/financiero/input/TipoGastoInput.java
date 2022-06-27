package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.financiero.TipoGasto;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

@Data
public class TipoGastoInput {
    private Long id;

    private Boolean isClasificacion;
    private Boolean activo;
    private Boolean autorizacion;

    private String descripcion;

    private String simbolo;

    private Long clasificacionGastoId;

    private Long cargoId;

    private Date creadoEn;

    private Long usuarioId;
}
