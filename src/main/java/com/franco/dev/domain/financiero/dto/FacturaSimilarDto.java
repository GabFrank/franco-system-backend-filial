package com.franco.dev.domain.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Datos de una factura ya emitida en el turno de caja que se parece a la que el
 * cajero está por generar. Se usa solo para mostrarle el aviso de posible duplicado
 * antes de emitirla; no se persiste.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FacturaSimilarDto {
    private Long facturaLegalId;
    /**
     * Número de factura en formato EEE-PPP-NNNNNNN.
     */
    private String numeroFactura;
    private LocalDateTime fecha;
    private Double totalFinal;
    private String clienteNombre;
}
