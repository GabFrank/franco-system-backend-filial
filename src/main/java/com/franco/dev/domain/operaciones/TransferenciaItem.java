package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.enums.TipoTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.operaciones.enums.TransferenciaItemMotivoModificacion;
import com.franco.dev.domain.operaciones.enums.TransferenciaItemMotivoRechazo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
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
@Table(name = "transferencia_item", schema = "operaciones")
@TypeDef(
        name = "transferencia_item_motivo_rechazo",
        typeClass = PostgreSQLEnumType.class
)
@TypeDef(
        name = "transferencia_item_motivo_modificacion",
        typeClass = PostgreSQLEnumType.class
)
public class TransferenciaItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferencia_id", nullable = true)
    private Transferencia transferencia;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "presentacion_pre_transferencia_id", nullable = true)
    private Presentacion presentacionPreTransferencia;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "presentacion_preparacion_id", nullable = true)
    private Presentacion presentacionPreparacion;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "presentacion_transporte_id", nullable = true)
    private Presentacion presentacionTransporte;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "presentacion_recepcion_id", nullable = true)
    private Presentacion presentacionRecepcion;

    @JoinColumn(name = "cantidad_pre_transferencia", nullable = true)
    private Double cantidadPreTransferencia;

    @JoinColumn(name = "cantidad_preparacion", nullable = true)
    private Double cantidadPreparacion;


    @JoinColumn(name = "cantidad_transporte", nullable = true)
    private Double cantidadTransporte;

    @JoinColumn(name = "cantidad_recepcion", nullable = true)
    private Double cantidadRecepcion;

    @JoinColumn(name = "observacion_pre_transferencia", nullable = true)
    private String observacionPreTransferencia;

    @JoinColumn(name = "observacion_preparacion", nullable = true)
    private String observacionPreparacion;

    @JoinColumn(name = "observacion_transporte", nullable = true)
    private String observacionTransporte;

    @JoinColumn(name = "observacion_recepcion", nullable = true)
    private String observacionRecepcion;

    @JoinColumn(name = "vencimiento_pre_transferencia", nullable = true)
    private LocalDateTime vencimientoPreTransferencia;

    @JoinColumn(name = "vencimiento_preparacion", nullable = true)
    private LocalDateTime vencimientoPreparacion;


    @JoinColumn(name = "vencimiento_transporte", nullable = true)
    private LocalDateTime vencimientoTransporte;


    @JoinColumn(name = "vencimiento_recepcion", nullable = true)
    private LocalDateTime vencimientoRecepcion;

    private Boolean poseeVencimiento;

    private Boolean activo;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_rechazo_pre_transferencia")
    @Type( type = "transferencia_item_motivo_rechazo")
    private TransferenciaItemMotivoRechazo motivoRechazoPreTransferencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_rechazo_preparacion")
    @Type( type = "transferencia_item_motivo_rechazo")
    private TransferenciaItemMotivoRechazo motivoRechazoPreparacion;


    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_rechazo_transporte")
    @Type( type = "transferencia_item_motivo_rechazo")
    private TransferenciaItemMotivoRechazo motivoRechazoTransporte;


    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_rechazo_recepcion")
    @Type( type = "transferencia_item_motivo_rechazo")
    private TransferenciaItemMotivoRechazo motivoRechazoRecepcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_modificacion_pre_transferencia")
    @Type( type = "transferencia_item_motivo_rechazo")
    private TransferenciaItemMotivoModificacion motivoModificacionPreTransferencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_modificacion_preparacion")
    @Type( type = "transferencia_item_motivo_rechazo")
    private TransferenciaItemMotivoModificacion motivoModificacionPreparacion;


    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_modificacion_transporte")
    @Type( type = "transferencia_item_motivo_rechazo")
    private TransferenciaItemMotivoModificacion motivoModificacionTransporte;


    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_modificacion_recepcion")
    @Type( type = "transferencia_item_motivo_rechazo")
    private TransferenciaItemMotivoModificacion motivoModificacionRecepcion;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

//    etapa: EtapaTransferencia
//    activo: Boolean
//    poseeVencimiento: Date
}