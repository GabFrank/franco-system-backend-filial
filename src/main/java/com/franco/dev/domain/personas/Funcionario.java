package com.franco.dev.domain.personas;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.empresarial.Sucursal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

//id bigserial PRIMARY KEY,
//persona_id int8,
//cargo_id int8,
//credito numeric,
//fecha_ingreso timestamp,
//sueldo numeric,
//sector int8,
//supervisado_por_id int8,
//sucursal_id int8,
//fase_prueba bool,
//diarista bool,
//usuario_id int8,

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "funcionario", schema = "personas")
public class Funcionario implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = true)
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id", nullable = true)
    private Cargo cargo;

    private Float credito;

    @Column(name = "fecha_ingreso")
    private LocalDateTime fechaIngreso;

    private Float sueldo;

    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @Column(name = "fase_prueba")
    private Boolean fasePrueba;

    @Column(name = "diarista")
    private Boolean diarista;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisado_por_id", nullable = true)
    private Funcionario supervisadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horario_id", nullable = true)
    private com.franco.dev.domain.administrativo.Horario horario;
}
