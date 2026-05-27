package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "gasto_rendicion", schema = "financiero")
public class GastoRendicion implements Identifiable<Long> {
    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
        @JoinColumn(name = "pre_gasto_id", referencedColumnName = "id"),
        @JoinColumn(name = "pre_gasto_sucursal_id", referencedColumnName = "sucursal_id")
    })
    private PreGasto preGasto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_gasto_id")
    private TipoGasto tipoGasto;

    @Column(name = "monto_total")
    private BigDecimal montoTotal;

    @Column(name = "foto_factura_url")
    private String fotoFacturaUrl;

    @Column(name = "foto_producto_url")
    private String fotoProductoUrl;

    // Campos Combustible / Repuesto
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ente_id")
    private Ente ente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gasolinera_id")
    private Gasolinera gasolinera;

    @Column(name = "km_actual")
    private BigDecimal kmActual;

    private BigDecimal litros;

    @Column(name = "precio_por_litro")
    private BigDecimal precioPorLitro;

    // Campos Herramienta / Mueble
    @Column(name = "ubicacion_provisoria")
    private String ubicacionProvisoria;

    // Campos Alimentacion (Reemplazo de GastoComensal)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "gasto_rendicion_funcionario",
        schema = "financiero",
        joinColumns = @JoinColumn(name = "gasto_rendicion_id"),
        inverseJoinColumns = @JoinColumn(name = "funcionario_id")
    )
    private List<Persona> funcionariosComensales;

    @Column(name = "establecimiento_alimentacion")
    private String establecimientoAlimentacion;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}