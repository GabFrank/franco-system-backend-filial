package com.franco.dev.domain.financiero;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "venta_credito_cuota", schema = "financiero")
public class VentaCreditoCuota implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_credito_id", nullable = true)
    private VentaCredito ventaCredito;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cobro_id", nullable = true)
    private Cobro cobro;

    private Double valor;

    private Boolean parcial;

    private Boolean activo;

    private LocalDateTime vencimiento;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



