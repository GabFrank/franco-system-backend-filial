package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.operaciones.enums.EstadoLote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Saldo de un lote expresado en la presentación con la que trabaja el operador.
 *
 * Las conversiones vienen resueltas del backend a propósito: el frontend es solo capa de
 * presentación y no debe hacer cuentas de unidades.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockLotePresentacionDto {

    private Long loteId;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    private LocalDate fechaRetiro;
    private EstadoLote estado;
    /** Saldo en unidades base, como vive en el ledger. */
    private Double cantidadDisponible;
    /** Presentaciones completas que entran en el saldo. Una caja es indivisible. */
    private Double cantidadDisponiblePresentacion;
    /** Unidades que quedan fuera de esas presentaciones completas. */
    private Double unidadesSobrantes;
    private Double unidadesPorPresentacion;
    private String presentacionDescripcion;
}
