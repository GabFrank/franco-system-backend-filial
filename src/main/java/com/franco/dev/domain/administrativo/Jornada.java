package com.franco.dev.domain.administrativo;

import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "jornada", schema = "administrativo")
@AllArgsConstructor
@NoArgsConstructor
public class Jornada implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDate fecha;

    @OneToOne
    @JoinColumn(name = "entrada_id")
    private Marcacion marcacionEntrada;

    @OneToOne
    @JoinColumn(name = "salida_id")
    private Marcacion marcacionSalida;

    @Column(name = "minutos_trabajados")
    private Long minutosTrabajados = 0L;

    @Column(name = "minutos_extras")
    private Long minutosExtras = 0L;

    @Column(name = "minutos_llegada_tardia")
    private Long minutosLlegadaTardia = 0L;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EstadoJornada estado;

    private String observacion;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        this.actualizadoEn = LocalDateTime.now();
    }
}