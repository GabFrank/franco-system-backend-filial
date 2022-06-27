package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalidaItemInput {
    private Long id;
    private Long salidaId;
    private Long productoId;
    private Long presentacionId;
    private Float cantidad;
    private String observacion;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
