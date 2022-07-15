package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FacturaDto {
    private String fecha;
    private String ruc;
    private String nombre;
    private String contado;
    private String credito;
    private String totalParcial;
    private String total;
    private String totalEnLetras;
    private String ivaParcial;
}
