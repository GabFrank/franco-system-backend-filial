package com.franco.dev.graphql.operaciones.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SesionInventarioInput implements Serializable {

    private Long id;
    private Long sucursalId;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}