package com.franco.dev.domain.administrativo;

import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.franco.dev.domain.EmbebedPrimaryKey;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "marcacion", schema = "administrativo")
@AllArgsConstructor
@NoArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
@IdClass(EmbebedPrimaryKey.class)
public class Marcacion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @Id
    @Column(name = "sucursal_id")
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_marcacion", columnDefinition = "administrativo.tipo_marcacion")
    @Type(type = "pgsql_enum")
    private TipoMarcacion tipo;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitud;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitud;

    @Column(name = "precision_gps")
    private Float precisionGps;

    @Column(name = "distancia_sucursal")
    private Integer distanciaSucursalMetros;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "device_info")
    private String deviceInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_entrada_id")
    private Sucursal sucursalEntrada;

    @Column(name = "fecha_entrada")
    private LocalDateTime fechaEntrada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_salida_id")
    private Sucursal sucursalSalida;

    @Column(name = "fecha_salida")
    private LocalDateTime fechaSalida;

    private Boolean presencial;

    private Long autorizacion;

    private String codigo;

    @Transient
    private Boolean esSalidaAlmuerzo;
}
