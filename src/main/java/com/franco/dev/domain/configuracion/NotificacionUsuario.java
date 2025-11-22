package com.franco.dev.domain.configuracion;

import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "notificacion_usuario", schema = "configuraciones")
public class NotificacionUsuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notificacion_id")
    private Notificacion notificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private String tokenFcm;

    @Enumerated(EnumType.STRING)
    private EstadoEnvio estadoEnvio;

    private LocalDateTime fechaEnvio;
    private LocalDateTime fechaEntrega;

    private Boolean leida = false;
    private LocalDateTime fechaLeida;

    private Boolean interactuada = false;
    private LocalDateTime fechaInteraccion;
    private String accionRealizada;

    private String mensajeError;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    private LocalDateTime actualizadoEn;
}
