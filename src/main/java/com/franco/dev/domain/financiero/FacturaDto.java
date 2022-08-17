package com.franco.dev.domain.financiero;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private String ivaParcial10;
    private String ivaParcial5;
    private String ivaParcial0;
    private String ivaFinal;
    private String direccion;
    private Venta venta;
    private FacturaLegalInput facturaLegalInput;
    private List<FacturaLegalItemInput> facturaLegalItemInputList;
}
