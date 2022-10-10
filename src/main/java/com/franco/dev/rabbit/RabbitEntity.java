package com.franco.dev.rabbit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RabbitEntity {
    private Long idSucursalOrigen;
    private Boolean propagado;
}
