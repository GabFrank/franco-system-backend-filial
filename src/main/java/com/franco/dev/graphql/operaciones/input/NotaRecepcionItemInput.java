package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotaRecepcionItemInput {
    private Long id;
    private Long notaRecepcionId;
    private Long pedidoItemId;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
