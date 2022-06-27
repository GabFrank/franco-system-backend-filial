package com.franco.dev.domain.empresarial;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "cargo", schema = "empresarial")
public class Cargo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String nombre;

    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisado_por_id", nullable = true)
    private Cargo supervisadoPor;

    @Column(name = "sueldo_base")
    private Float sueldoBase;

    private Date creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}