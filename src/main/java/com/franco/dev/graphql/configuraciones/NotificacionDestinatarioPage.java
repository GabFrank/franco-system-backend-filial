package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuracion.NotificacionDestinatario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificacionDestinatarioPage {
    private List<NotificacionDestinatario> content;
    private Integer pageNumber;
    private Integer pageSize;
    private Long totalElements;
    private Integer totalPages;
}
