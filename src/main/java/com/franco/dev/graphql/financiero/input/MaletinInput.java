package com.franco.dev.graphql.financiero.input;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Date;

@Data
public class MaletinInput {
    private Long id;

    private String descripcion;

    private Boolean activo;
    private Boolean abierto;

    private LocalDateTime creadoEn;

    private Long usuarioId;

    private Long sucursalId;
}
