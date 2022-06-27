package com.franco.dev.graphql.productos.input;

import com.franco.dev.rabbit.RabbitEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CodigoInput extends RabbitEntity {
    private Long id;
    private String codigo;
    private Boolean principal;
    private Boolean activo;
    private Long presentacionId;
    private Long usuarioId;
}
