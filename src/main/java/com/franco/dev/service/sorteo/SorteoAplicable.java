package com.franco.dev.service.sorteo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Sorteo en el que participa una venta, con los tickets que le corresponden. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SorteoAplicable {
    private Long sorteoId;
    private String nombre;
    private String descripcion;
    private Integer cantidadTickets;
    private LocalDate fechaSorteo;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    /** En texto, por ejemplo "1 x Moto 110cc". */
    private List<String> premios;
}
