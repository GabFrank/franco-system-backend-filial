package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PdvCajaBalanceDto {
    private Long idCaja;
    private Sucursal sucursal;
    private LocalDateTime creadoEn;
    private Usuario usuario;
    private Double totalGsAper = 0.0;
    private Double totalRsAper = 0.0;
    private Double totalDsAper = 0.0;
    private Double totalGsCierre = 0.0;
    private Double totalRsCierre = 0.0;
    private Double totalDsCierre = 0.0;
    private Double totalVentaGs = 0.0;
    private Double totalVentaRs = 0.0;
    private Double totalVentaDs = 0.0;
    private Double totalTarjeta = 0.0;
    private Double totalCheque = 0.0;
    private Double diferenciaGs = 0.0;
    private Double diferenciaRs = 0.0;
    private Double diferenciaDs = 0.0;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
    private Double totalRetiroGs = 0.0;
    private Double totalRetiroRs = 0.0;
    private Double totalRetiroDs = 0.0;
    private Double totalGastoGs = 0.0;
    private Double totalGastoRs = 0.0;
    private Double totalGastoDs = 0.0;
    private Double totalDescuento = 0.0;
    private Double totalAumento = 0.0;
    private Double totalCanceladasGs = 0.0;
    private Double totalCanceladasRs = 0.0;
    private Double totalCanceladasDs = 0.0;
    private Double totalCredito = 0.0;
    private Double vueltoGs = 0.0;
    private Double vueltoRs = 0.0;
    private Double vueltoDs = 0.0;
}
