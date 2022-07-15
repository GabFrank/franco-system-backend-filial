package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VentaItemDto {
    private String cantidad;
    private String descripcion;
    private String precioUnitario;
    private String totalParcial;
}
