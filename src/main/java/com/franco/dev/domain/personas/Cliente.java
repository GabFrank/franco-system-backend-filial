package com.franco.dev.domain.personas;

import com.fasterxml.jackson.annotation.JsonView;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.utilitarios.JsonIdView;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "cliente", schema = "personas")
@TypeDef(
        name = "tipo_cliente",
        typeClass = PostgreSQLEnumType.class
)
public class Cliente implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @JsonView(JsonIdView.Id.class)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    @Type(type = "tipo_cliente")
    private TipoCliente tipo;

    private Float credito;

    private String codigo;

    private Boolean tributa;

    private Boolean verificadoSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = true)
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "tipo_contribuyente")
    private Integer tipoContribuyente;
}



