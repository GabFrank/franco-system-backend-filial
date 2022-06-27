package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.Entrada;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

@Data
public class EntradaItemInput {
    private Long id;
    private Long entradaId;
    private Long productoId;
    private Long presentacionId;
    private Float cantidad;
    private String observacion;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
