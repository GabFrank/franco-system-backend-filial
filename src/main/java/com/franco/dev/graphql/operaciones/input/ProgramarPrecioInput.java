package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.MomentoCambio;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import lombok.Data;

import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

@Data
public class ProgramarPrecioInput {
    private Long id;
    private Long precioId;
    private MomentoCambio momentoCambio;
    private Double nuevoPrecio;
    private LocalDateTime fechaCambio;
    private Double cantidad;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
