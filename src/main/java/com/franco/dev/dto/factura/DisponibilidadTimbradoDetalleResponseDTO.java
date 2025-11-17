package com.franco.dev.dto.factura;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisponibilidadTimbradoDetalleResponseDTO {

    private Boolean disponible;

    private String mensaje;

    private Long timbradoDetalleId;

    private Long numeroActual;

    private Long rangoDesde;

    private Long rangoHasta;

    private Long numerosDisponibles;

    private Boolean esElectronico;

    private String numeroTimbrado;

    private Boolean activo;

    private Boolean timbradoActivo;
}

