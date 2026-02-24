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
import java.time.LocalTime;

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

    @OneToOne
    @JoinColumn(name = "marcacion_salida_almuerzo_id")
    private Marcacion marcacionSalidaAlmuerzo;

    @OneToOne
    @JoinColumn(name = "marcacion_entrada_almuerzo_id")
    private Marcacion marcacionEntradaAlmuerzo;

    @Column(name = "minutos_trabajados")
    private Long minutosTrabajados = 0L;

    @Column(name = "minutos_extras")
    private Long minutosExtras = 0L;

    @Column(name = "minutos_llegada_tardia")
    private Long minutosLlegadaTardia = 0L;

    @Column(name = "minutos_llegada_tardia_almuerzo")
    private Long minutosLlegadaTardiaAlmuerzo = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "turno")
    private com.franco.dev.domain.administrativo.enums.Turno turno;

    @Column(name = "hora_entrada_horario")
    private LocalTime horaEntradaHorario;

    @Column(name = "hora_salida_horario")
    private LocalTime horaSalidaHorario;

    @Column(name = "inicio_descanso_horario")
    private LocalTime inicioDescansoHorario;

    @Column(name = "fin_descanso_horario")
    private LocalTime finDescansoHorario;

    @Column(name = "tolerancia_minutos_horario")
    private Integer toleranciaMinutosHorario;

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