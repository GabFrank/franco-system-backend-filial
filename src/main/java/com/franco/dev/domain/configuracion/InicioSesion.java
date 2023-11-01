package com.franco.dev.domain.configuracion;

import com.franco.dev.domain.configuracion.enums.TipoDispositivo;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "inicio_sesion", schema = "configuraciones")
@TypeDef(
        name = "tipo_dispositivo",
        typeClass = PostgreSQLEnumType.class
)
public class InicioSesion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dispositivo")
    @Type( type = "tipo_dispositivo")
    private TipoDispositivo tipoDespositivo;

    private String idDispositivo;

    private String token;

    @Column(name = "hora_inicio")
    private LocalDateTime horaInicio;

    @Column(name = "hora_fin")
    private LocalDateTime horaFin;

    @CreationTimestamp
    private LocalDateTime creadoEn;

}



