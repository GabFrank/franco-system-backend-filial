package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.domain.operaciones.enums.TipoEntrada;
import com.franco.dev.domain.personas.Funcionario;
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
@Table(name = "retiro", schema = "financiero")
@TypeDef(
        name = "estado_retiro",
        typeClass = PostgreSQLEnumType.class
)
public class Retiro implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String observacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type( type = "estado_retiro")
    private EstadoRetiro estado;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id", nullable = true)
    private Funcionario responsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_salida_id", nullable = true)
    private PdvCaja cajaSalida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_entrada_id", nullable = true)
    private PdvCaja cajaEntrada;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



