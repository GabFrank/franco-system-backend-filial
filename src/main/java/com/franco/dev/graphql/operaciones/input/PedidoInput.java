package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PedidoInput {
    private Long id;
    private Long vendedorId;
    private Long proveedorId;
    private LocalDateTime fechaDeEntrega;
    private Long formaPagoId;
    private PedidoEstado estado;
    private Long monedaId;
    private Integer plazoCredito;
    private Float descuento;
    private Float valorTotal;
    private Long usuarioId;
    private LocalDateTime creadoEn;
    private List<PedidoItemInput> pedidoItemInputList;
}


