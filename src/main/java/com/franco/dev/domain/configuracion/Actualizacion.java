package com.franco.dev.domain.configuracion;

import com.franco.dev.domain.configuracion.enums.NivelActualizacion;
import com.franco.dev.domain.configuracion.enums.TipoActualizacion;
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

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "actualizacion", schema = "configuraciones")
@TypeDef(
        name = "tipo_actualizacion",
        typeClass = PostgreSQLEnumType.class
)
@TypeDef(
        name = "nivel_actualizacion",
        typeClass = PostgreSQLEnumType.class
)
public class Actualizacion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private String currentVersion;
    private Boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    @Type( type = "tipo_actualizacion")
    private TipoActualizacion tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel")
    @Type( type = "nivel_actualizacion")
    private NivelActualizacion nivel;

    private String title;
    private String msg;
    private String btn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    private LocalDateTime creadoEn;

}



