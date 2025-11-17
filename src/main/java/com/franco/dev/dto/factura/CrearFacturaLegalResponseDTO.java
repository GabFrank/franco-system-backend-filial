package com.franco.dev.dto.factura;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearFacturaLegalResponseDTO {

    private Long id;

    private Integer numeroFactura;

    private String nombre;

    private String ruc;

    private String direccion;

    private LocalDateTime fecha;

    private Double totalFinal;

    private Boolean esElectronica;

    private String cdc;

    private String urlQr;

    private String estadoDocumentoElectronico;

    private String mensajeRespuestaSifen;

    private Boolean documentoElectronicoGenerado;
}

