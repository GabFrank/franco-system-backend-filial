package com.franco.dev.domain.productos;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.rabbit.RabbitEntity;
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
@Table(name = "codigo", schema = "productos")
public class Codigo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String codigo;

    private Boolean principal;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id", nullable = true)
    private Presentacion presentacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



