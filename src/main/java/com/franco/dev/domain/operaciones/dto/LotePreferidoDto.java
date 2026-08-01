package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lote que el cajero eligió a mano para un ítem de venta, con la cantidad que quiere sacar de ahí.
 *
 * Es una preferencia, no una orden: FEFO la recorta al saldo real del lote y completa el faltante
 * con otros lotes. Ver {@code LoteFefoService.asignarConPreferencia}.
 *
 * La cantidad viaja EN PRESENTACIONES, que es la unidad en la que el cajero ve y elige. La
 * conversión a unidades base la hace el backend.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LotePreferidoDto {
    private Long loteId;
    private Double cantidad;
}
