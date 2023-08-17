package com.franco.dev.domain.productos;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "presentacion", schema = "productos")
public class Presentacion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private String descripcion;
    private Double cantidad;
    private Boolean activo;
    private Boolean principal;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = true)
    private Producto producto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_presentacion_id", nullable = true)
    private TipoPresentacion tipoPresentacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

}