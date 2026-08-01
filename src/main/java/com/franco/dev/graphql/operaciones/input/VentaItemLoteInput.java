package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

/**
 * Lote elegido a mano por el cajero para un ítem de venta.
 *
 * La cantidad va EN PRESENTACIONES, la misma unidad que muestra el selector. La conversión a
 * unidades base la hace el backend.
 */
@Data
public class VentaItemLoteInput {
    private Long loteId;
    private Double cantidad;
}
