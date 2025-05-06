package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MovimientoStockCantidadAndIdDto {
    private Double cantidad;
    private Long lastId;
    private Long cantItens;
}
