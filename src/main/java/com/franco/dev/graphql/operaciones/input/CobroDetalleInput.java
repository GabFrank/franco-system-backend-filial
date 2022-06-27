package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Cobro;
import lombok.Data;

import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

@Data
public class CobroDetalleInput {
    private Long id;
    private Long cobroId;
    private Long monedaId;
    private Double cambio;
    private Long formaPagoId;
    private Double valor;
    private Boolean descuento;
    private Boolean aumento;
    private Boolean pago;
    private Boolean vuelto;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
