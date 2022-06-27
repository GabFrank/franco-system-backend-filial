package com.franco.dev.graphql.productos.input;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
public class ComboInput {
    private Long id;
    private Long productoId;
    private Double valorTotal;
    private Long usuarioId;
}
