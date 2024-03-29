package com.franco.dev.domain.operaciones;

import com.fasterxml.jackson.annotation.JsonView;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.JsonIdView;
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
@Table(name = "venta", schema = "operaciones")
@TypeDef(
        name = "venta_tipo_venta",
        typeClass = PostgreSQLEnumType.class
)
@TypeDef(
        name = "venta_estado",
        typeClass = PostgreSQLEnumType.class
)
public class Venta implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(JsonIdView.Id.class)
    private Long id;

    private Long sucursalId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "forma_pago_id", nullable = true)
    private FormaPago formaPago;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cobro_id", nullable = true)
    private Cobro cobro;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "caja_id", nullable = true)
    private PdvCaja caja;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "venta_estado")
    private VentaEstado estado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "total_gs")
    private Double totalGs;
    @Column(name = "total_rs")
    private Double totalRs;
    @Column(name = "total_ds")
    private Double totalDs;
}