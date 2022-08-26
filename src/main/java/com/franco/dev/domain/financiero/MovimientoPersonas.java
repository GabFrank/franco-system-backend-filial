package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.TipoMovimientoPersonas;
import com.franco.dev.domain.personas.Persona;
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
@Table(name = "movimiento_personas", schema = "financiero")
@TypeDef(
        name = "tipo_movimiento_personas",
        typeClass = PostgreSQLEnumType.class
)
public class MovimientoPersonas implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String observacion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "persona_id", nullable = true)
    private Persona persona;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    @Type(type = "tipo_movimiento_personas")
    private TipoMovimientoPersonas tipo;

    private Long referenciaId;

    private Double valorTotal;

    private Boolean activo;

    private LocalDateTime vencimiento;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



