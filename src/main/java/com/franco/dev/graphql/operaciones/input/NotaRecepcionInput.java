package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.financiero.Documento;
import com.franco.dev.domain.operaciones.Compra;
import com.franco.dev.domain.operaciones.Pedido;
import lombok.Data;

import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

@Data
public class NotaRecepcionInput {
    private Long id;
    private Long pedidoId;
    private Long compraId;
    private Long documentoId;
    private Double valor;
    private Double descuento;
    private Integer numero;
    private Integer timbrado;
    private Boolean pagado;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
