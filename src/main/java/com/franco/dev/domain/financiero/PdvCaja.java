package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
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
@Table(name = "pdv_caja", schema = "financiero")
@TypeDef(
        name = "pdv_caja_estado",
        typeClass = PostgreSQLEnumType.class
)
public class PdvCaja implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @SequenceGenerator(name = "financiero.pdv_caja_id_seq",
            sequenceName = "financiero.pdv_caja_id_seq",
            allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "financiero.pdv_caja_id_seq")
    private Long id;

    private Long sucursalId;

    private String descripcion;
    private String observacion;
    private Boolean activo;
    private Boolean tuvoProblema;

    @Column(name = "fecha_apertura")
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "pdv_caja_estado")
    private PdvCajaEstado estado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "maletin_id", nullable = true)
    private Maletin maletin;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conteo_apertura_id", nullable = true)
    private Conteo conteoApertura;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conteo_cierre_id", nullable = true)
    private Conteo conteoCierre;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean verificado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verificado_por_id", nullable = true)
    private Usuario verificadoPor;
}



