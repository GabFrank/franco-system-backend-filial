package com.franco.dev.domain.dto;

import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockPorTipoMovimientoDto {
    private TipoMovimiento tipoMovimiento;
    private Double stock;
}

