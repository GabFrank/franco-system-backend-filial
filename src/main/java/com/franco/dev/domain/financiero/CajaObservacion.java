package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CajaObservacion {

    private Long id;
    private String descripcion;
    private LocalDateTime creadoEn;
}
