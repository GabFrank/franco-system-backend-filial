package com.franco.dev.domain.configuracion;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SincronizacionStatus {
    private Boolean finalizado;
    private String mensaje;
}
