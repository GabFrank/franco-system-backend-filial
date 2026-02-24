package com.franco.dev.domain.administrativo;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "horario", schema = "administrativo")
public class Horario implements Identifiable<Long>, Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    private String descripcion;

    @Column(name = "hora_entrada")
    private LocalTime horaEntrada;

    @Column(name = "hora_salida")
    private LocalTime horaSalida;

    @Column(name = "tolerancia_minutos")
    private Integer toleranciaMinutos;

    @Column(name = "inicio_descanso")
    private LocalTime inicioDescanso;

    @Column(name = "fin_descanso")
    private LocalTime finDescanso;

    @Enumerated(EnumType.STRING)
    private com.franco.dev.domain.administrativo.enums.Turno turno;

    @ElementCollection(targetClass = com.franco.dev.domain.administrativo.enums.Dia.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "horario_dias", schema = "administrativo", joinColumns = @JoinColumn(name = "horario_id"))
    @Column(name = "dia")
    private java.util.Set<com.franco.dev.domain.administrativo.enums.Dia> dias;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @PrePersist
    public void prePersist() {
        this.creadoEn = LocalDateTime.now();
    }
}
