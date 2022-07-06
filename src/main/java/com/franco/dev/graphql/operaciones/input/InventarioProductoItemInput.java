package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.InventarioProductoEstado;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventarioProductoItemInput {
    private Long id;
    private Long idCentral;
    private Long idOrigen;
    private Long inventarioProductoId;
    private Long presentacionId;
    private Long zonaId;
    private Double cantidad;
    private Double cantidadFisica;
    private String vencimiento;
    private InventarioProductoEstado estado;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
