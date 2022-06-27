package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.personas.Usuario;
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
@Table(name = "transferencia", schema = "operaciones")
@TypeDef(
        name = "tipo_transferencia",
        typeClass = PostgreSQLEnumType.class
)
@TypeDef(
        name = "transferencia_estado",
        typeClass = PostgreSQLEnumType.class
)
@TypeDef(
        name = "etapa_transferencia",
        typeClass = PostgreSQLEnumType.class
)
public class Transferencia implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_origen_id", nullable = true)
    private Sucursal sucursalOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_destino_id", nullable = true)
    private Sucursal sucursalDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type( type = "transferencia_estado")
    private TransferenciaEstado estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    @Type( type = "tipo_transferencia")
    private TipoTransferencia tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa")
    @Type( type = "etapa_transferencia")
    private EtapaTransferencia etapa;

    @Column(name = "is_origen")
    private Boolean isOrigen;

    @Column(name = "is_destino")
    private Boolean isDestino;

    private String observacion;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_pre_transferencia_id", nullable = true)
    private Usuario usuarioPreTransferencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_preparacion_id", nullable = true)
    private Usuario usuarioPreparacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_transporte_id", nullable = true)
    private Usuario usuarioTransporte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_recepcion_id", nullable = true)
    private Usuario usuarioRecepcion;
}