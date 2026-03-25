package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.financiero.enums.PeriodicidadGasto;
import com.franco.dev.domain.financiero.enums.TipoFijoVariable;
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
import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "gasto_continuo_config", schema = "financiero")
public class GastoContinuoConfig implements Identifiable<Long> {
    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_fijo_variable")
    private TipoFijoVariable tipoFijoVariable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ente_id")
    private Ente ente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_gasto_id")
    private TipoGasto tipoGasto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Persona proveedor;

    @Enumerated(EnumType.STRING)
    private PeriodicidadGasto periodicidad;

    @Column(name = "dia_vencimiento")
    private Integer diaVencimiento;

    @Column(name = "meses_activos", columnDefinition = "jsonb")
    private String mesesActivos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id")
    private Moneda moneda;

    @Column(name = "valor_fijo")
    private BigDecimal valorFijo;

    @Column(name = "vigencia_contrato")
    private LocalDate vigenciaContrato;

    private String nis;

    @Column(name = "nro_reloj")
    private String nroReloj;

    @Column(name = "lectura_inicial")
    private BigDecimal lecturaInicial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "titular_factura_id")
    private Persona titularFactura;

    @Column(name = "velocidad_subida")
    private String velocidadSubida;

    @Column(name = "velocidad_bajada")
    private String velocidadBajada;

    @Column(name = "horario_entrada")
    private LocalTime horarioEntrada;

    @Column(name = "horario_salida")
    private LocalTime horarioSalida;

    private String poliza;

    @Column(name = "fecha_ultimo_pago")
    private LocalDate fechaUltimoPago;

    @Column(name = "dias_recoleccion", columnDefinition = "jsonb")
    private String diasRecoleccion;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}