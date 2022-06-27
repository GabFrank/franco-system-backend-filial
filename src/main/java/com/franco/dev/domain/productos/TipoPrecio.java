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
@Table(name = "tipo_precio", schema = "productos")
public class TipoPrecio implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private String descripcion;
    private Boolean autorizacion;
    private Boolean activo;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

}