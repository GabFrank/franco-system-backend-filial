package com.franco.dev.domain.productos.pdv;

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
@Table(name = "pdv_categoria", schema = "productos")
public class PdvCategoria implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private String descripcion;
    private Boolean activo;
    private Integer posicion;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

}