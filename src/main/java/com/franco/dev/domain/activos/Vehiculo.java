package com.franco.dev.domain.activos;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "vehiculo", schema = "vehiculos")
public class Vehiculo implements Identifiable<Long> {

        private static final long serialVersionUID = 1L;

        @Id
        @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
        @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "modelo_id", nullable = true)
        private Modelo modelo;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "tipo_vehiculo", nullable = true)
        private TipoVehiculo tipoVehiculo;

        private String chapa;

        private String color;

        @Column(name = "anho")
        private Integer anho;

        private Boolean documentacion;

        private Boolean refrigerado;

        private Boolean nuevo;

        @Column(name = "fecha_adquisicion")
        private LocalDate fechaAdquisicion;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "propietario_id")
        private com.franco.dev.domain.personas.Persona propietario;

        @Column(name = "identificador_interno")
        private String identificadorInterno;

        @Column(name = "valor_estimado")
        private BigDecimal valorEstimado;

        @Column(name = "valor_estimado_pyg")
        private BigDecimal valorEstimadoPyg;

        @Column(name = "valor_estimado_brl")
        private BigDecimal valorEstimadoBrl;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "usuario_id", nullable = true)
        private Usuario usuario;

        @Column(name = "creado_en")
        private LocalDateTime creadoEn;
}
