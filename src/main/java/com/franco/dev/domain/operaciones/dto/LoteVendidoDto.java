package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Un lote del que salió parte de un ítem de venta, tal como se necesita para imprimirlo en el
 * ticket. Es una proyección del ledger operaciones.movimiento_stock_lote resuelta contra el
 * maestro operaciones.lote.
 *
 * Un mismo ítem puede tener varias filas: FEFO reparte la cantidad entre los lotes que hagan
 * falta cuando ninguno alcanza solo.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoteVendidoDto {

    /** Id del venta_item del que este lote es desglose. Es el {@code referencia} del ledger. */
    private Long ventaItemId;

    /**
     * Número de lote. Vive en la fila del ledger y no en el maestro a propósito: si el maestro
     * todavía no replicó, el ticket igual puede imprimir el número.
     */
    private String numeroLote;

    /** Vencimiento del maestro. Null si el lote no lo tiene cargado o todavía no replicó. */
    private LocalDate fechaVencimiento;

    /**
     * Cantidad en unidades base tal como vive en el ledger: NEGATIVA, porque una venta es una
     * salida. Quien la muestra la da vuelta.
     */
    private Double cantidad;
}
