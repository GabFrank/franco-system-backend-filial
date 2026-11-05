package com.franco.dev.service.impresion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una participacion individual de un sorteo, lista para imprimir. Valores planos: el que imprime
 * no tiene que releer nada de la base.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SorteoTicketDto {
    String nombreSorteo;
    String descripcion;
    List<String> premios;
    LocalDate fechaDesde;
    LocalDate fechaHasta;
    LocalDate fechaSorteo;
    String sucursal;
    Long ventaId;
    LocalDateTime fecha;
    Double totalGs;
    String codigo;
    /** Posicion de este ticket dentro de la venta (1..total). */
    int numero;
    int total;
    String clienteNombre;
    String clienteDocumento;
    boolean reimpresion;
}
