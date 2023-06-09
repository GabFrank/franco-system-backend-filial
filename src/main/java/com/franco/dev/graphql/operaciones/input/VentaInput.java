package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.VentaEstado;
import lombok.Data;

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
    private Long sucursalId;
}
