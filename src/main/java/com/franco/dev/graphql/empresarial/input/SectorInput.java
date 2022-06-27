package com.franco.dev.graphql.empresarial.input;

import com.franco.dev.domain.empresarial.Sucursal;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SectorInput {
    private Long id;
    private String descripcion;
    private Sucursal sucursal;
    private Boolean activo;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
