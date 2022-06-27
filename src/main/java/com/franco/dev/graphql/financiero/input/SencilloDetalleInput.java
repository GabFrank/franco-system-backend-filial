package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.Sencillo;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

@Data
public class SencilloDetalleInput {
    private Long id;

    private Long sencilloId;

    private Long monedaId;

    private Long cambioId;

    private Double cantidad;

    private Date creadoEn;

    private Long usuarioId;
}
