package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.operaciones.enums.CompraEstado;
import com.franco.dev.domain.operaciones.enums.CompraTipoBoleta;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CompraInput {
    private Long id;
    private Long proveedorId;
    private LocalDate fecha;
    private Long monedaId;
    private String nroNota;
    private Long formaPagoId;
    private CompraEstado estado;
    private Double valorParcial;
    private Double descuento;
    private Double valorTotal;
    private Long pedidoId;
    private CompraTipoBoleta tipoBoleta;
    private String imagenes;
    private Long usuarioId;
}
