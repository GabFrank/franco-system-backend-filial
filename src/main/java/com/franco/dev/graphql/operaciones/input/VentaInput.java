package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import lombok.Data;

import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

@Data
public class VentaInput {
    private Long id;
    private Long clienteId;
    private Long formaPagoId;
    private VentaEstado estado;
    private LocalDateTime creadoEn;
    private Long cobroId;
    private Long cajaId;
    private Long usuarioId;
    private Double totalGs;
    private Double totalRs;
    private Double totalDs;
}
