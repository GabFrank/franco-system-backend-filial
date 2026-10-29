package com.franco.dev.service.sorteo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Resumen de los tickets de sorteo que salieron con una venta, para el aviso del desktop. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SorteoTicketInfo {
    private String nombreSorteo;
    private Integer cantidad;
}
