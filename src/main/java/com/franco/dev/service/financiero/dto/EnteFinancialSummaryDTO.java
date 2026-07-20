package com.franco.dev.service.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnteFinancialSummaryDTO {
    private Long enteId;
    private String descripcion;
    private BigDecimal montoTotal;
    private BigDecimal montoYaPagado;
    private BigDecimal montoPendiente;
    private Integer cuotasTotales;
    private Integer cuotasPagadas;
    private Integer cuotasFaltantes;
    private Integer diaVencimiento;
    private Integer diasParaVencer;
    private String estadoCuota;
    private String monedaSimbolo;
    private Long monedaId;
    private String proveedorNombre;
    private Long proveedorId;
    private String tipoGastoSugeridoId;
    private String situacionPago;
    private Double porcentajePagado;
    private BigDecimal montoSugerido;
    private String descripcionSugerida;
    private Boolean autocompletarMonto;
    private Integer numeroCuotaActual;
    private java.time.LocalDate fechaVencimientoSugerida;
}
