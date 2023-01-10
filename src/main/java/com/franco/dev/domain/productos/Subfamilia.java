package com.franco.dev.domain.productos;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "subfamilia", schema = "productos")
public class Subfamilia implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private String nombre;
    private String descripcion;
    private Boolean activo;
    private String icono;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_familia_id", nullable = false)
    @JsonIgnore
    private Subfamilia subfamilia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuarioId;

    private Integer posicion;
}