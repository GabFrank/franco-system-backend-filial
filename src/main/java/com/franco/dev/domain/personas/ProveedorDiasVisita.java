package com.franco.dev.domain.personas;

import com.franco.dev.domain.general.enums.DiasSemana;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDef(
        name = "dias_semana",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "proveedor_dias_visita", schema = "personas")
public class ProveedorDiasVisita implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = true)
    private Proveedor proveedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia")
    @Type(type = "dias_semana")
    private DiasSemana dia;

    private Integer hora;

    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

}



