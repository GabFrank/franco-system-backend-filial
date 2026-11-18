package com.franco.dev.domain.productos.dto;

import com.franco.dev.domain.financiero.Moneda;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Un precio de venta en otra moneda que la de la empresa (V110.5). */
@Data
@AllArgsConstructor
public class PrecioEnOtraMoneda {
    private Long precioId;
    private Moneda moneda;
}
