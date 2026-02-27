package com.franco.dev.domain.administrativo;

import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.EmbebedPrimaryKey;
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
@IdClass(EmbebedPrimaryKey.class)
public class Jornada implements Serializable {

        private static final long serialVersionUID = 1L;

        @Id
        private Long id;

        @Id
        @Column(name = "sucursal_id")
        private Long sucursalId;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "usuario_id")
        private Usuario usuario;

        @Column(nullable = false)
        private LocalDate fecha;

        @OneToOne
        @JoinColumns(value = {
                        @JoinColumn(name = "entrada_id", referencedColumnName = "id"),
                        @JoinColumn(name = "entrada_sucursal_id", referencedColumnName = "sucursal_id")
        })
        private Marcacion marcacionEntrada;

        @OneToOne
        @JoinColumns(value = {
                        @JoinColumn(name = "salida_id", referencedColumnName = "id"),
                        @JoinColumn(name = "salida_sucursal_id", referencedColumnName = "sucursal_id")
        })
        private Marcacion marcacionSalida;

        @OneToOne
        @JoinColumns(value = {
                        @JoinColumn(name = "marcacion_salida_almuerzo_id", referencedColumnName = "id"),
                        @JoinColumn(name = "marcacion_salida_almuerzo_suc_id", referencedColumnName = "sucursal_id")
        })
        private Marcacion marcacionSalidaAlmuerzo;

        @OneToOne
        @JoinColumns(value = {
                        @JoinColumn(name = "marcacion_entrada_almuerzo_id", referencedColumnName = "id"),
                        @JoinColumn(name = "marcacion_entrada_almuerzo_suc_id", referencedColumnName = "sucursal_id")
        })
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
