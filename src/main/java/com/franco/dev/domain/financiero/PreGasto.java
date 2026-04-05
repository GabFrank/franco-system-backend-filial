package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pre_gasto", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class PreGasto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @Id
    @Column(name = "sucursal_id")
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_id")
    private Persona funcionario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ente_id")
    private Ente ente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_gasto_id")
    private TipoGasto tipoGasto;

    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id")
    private Moneda moneda;

    @Column(name = "monto_solicitado")
    private BigDecimal montoSolicitado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_caja_id")
    private Sucursal sucursalCaja;

    @Enumerated(EnumType.STRING)
    private EstadoPreGasto estado;

    @Column(name = "qr_token")
    private String qrToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id")
    private Persona autorizadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegado_a_id")
    private Persona delegadoA;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "monto_retirado")
    private BigDecimal montoRetirado;

    @Column(name = "monto_gastado")
    private BigDecimal montoGastado;

    @Column(name = "saldo_devolver")
    private BigDecimal saldoDevolver;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}