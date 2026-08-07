package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

/**
 * Lote elegido a mano por el cajero para un ítem de venta.
 *
 * La cantidad va EN UNIDADES BASE, la misma unidad que muestra el selector del POS y en la que
 * vive el ledger. No hay conversión en el medio: el cajero ve unidades y eso es lo que llega.
 */
@Data
public class VentaItemLoteInput {
    private Long loteId;
    private Double cantidad;
}
