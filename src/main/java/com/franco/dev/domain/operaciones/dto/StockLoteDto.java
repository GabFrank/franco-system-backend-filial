package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.operaciones.enums.EstadoLote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Saldo disponible de un lote en una sucursal. Es el resultado de agregar el ledger
 * operaciones.movimiento_stock_lote resolviendo los datos contra el maestro operaciones.lote
 * (equivalente a la vista operaciones.v_stock_lote).
 *
 * Es el DTO que consume FEFO y el que se expone por GraphQL: nunca se devuelve la entidad.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockLoteDto {

    private Long loteId;
    private Long productoId;
    private Long sucursalId;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    /** Fecha por la que ordena FEFO. Null si el lote no tiene retiro definido. */
    private LocalDate fechaRetiro;
    /** Solo los LIBERADO se pueden vender. Null si el maestro todavía no replicó. */
    private EstadoLote estado;
    /** Saldo en unidades base, como vive en el ledger. */
    private Double cantidadDisponible;
}
