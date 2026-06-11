package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ente_vinculacion", schema = "financiero")
public class EnteVinculacion implements Identifiable<Long> {
    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ente_id")
    private Ente ente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    private Sucursal sucursal;

    @Column(name = "es_propio")
    private Boolean esPropio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alquiler_proveedor_id")
    private Persona alquilerProveedor;

    @Column(name = "alquiler_monto")
    private BigDecimal alquilerMonto;

    @Column(name = "alquiler_dia_vencimiento")
    private Integer alquilerDiaVencimiento;

    @Column(name = "alquiler_vigencia")
    private LocalDate alquilerVigencia;

    private String observacion;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}