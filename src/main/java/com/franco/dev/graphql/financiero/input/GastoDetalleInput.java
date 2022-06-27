package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.Moneda;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;
import java.util.Date;

@Data
public class GastoDetalleInput {
    private Long id;

    private Long gastoId;

    private Long monedaId;

    private Double cambio;

    private Boolean vuelto;

    private Double cantidad;

    private String codigo;

    private LocalDateTime creadoEn;

    private Long usuarioId;
}
