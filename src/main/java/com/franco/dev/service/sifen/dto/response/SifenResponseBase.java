package com.franco.dev.service.sifen.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public abstract class SifenResponseBase {
    private boolean procesamientoCorrecto;
    private String codigoRespuesta;
    private String mensajeRespuesta;
    private LocalDateTime timestamp = LocalDateTime.now();
}
