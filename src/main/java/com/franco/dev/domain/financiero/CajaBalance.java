package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CajaBalance {
    private Long cajaId;
    private Double totalAperGs;
    private Double totalAperRs;
    private Double totalAperDs;
    private Double totalCierreGs;
    private Double totalCierreRs;
    private Double totalCierreDs;
    private Double totalVentaGs;
    private Double totalVentaRs;
    private Double totalVentaDs;
    private Double totalTarjeta;
    private Double totalCredito;
    private Double totalDescuento;
    private Double totalAumento;
    private Double totalRetiroGs;
    private Double totalRetiroRs;
    private Double totalRetiroDs;
    private Double totalGastoGs;
    private Double totalGastoRs;
    private Double totalGastoDs;
    private Double totalCanceladasGs;
    private Double totalCanceladasRs;
    private Double totalCanceladasDs;
    private Double vueltoGs;
    private Double vueltoRs;
    private Double vueltoDs;
}



