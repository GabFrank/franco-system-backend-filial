package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.financiero.enums.SituacionPagoEnte;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ente_financiero", schema = "financiero")
public class EnteFinanciero implements Identifiable<Long> {
    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ente_id")
    private Ente ente;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacion_pago")
    private SituacionPagoEnte situacionPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Persona proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id")
    private Moneda moneda;

    @Column(name = "monto_total")
    private BigDecimal montoTotal;

    @Column(name = "monto_ya_pagado")
    private BigDecimal montoYaPagado;

    @Column(name = "cantidad_cuotas")
    private Integer cantidadCuotas;

    @Column(name = "dia_vencimiento")
    private Integer diaVencimiento;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}