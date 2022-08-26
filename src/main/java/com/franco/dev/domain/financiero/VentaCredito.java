package com.franco.dev.domain.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.financiero.enums.TipoConfirmacion;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "venta_credito", schema = "financiero")
@TypeDef(
        name = "tipo_confirmacion",
        typeClass = PostgreSQLEnumType.class
)
@TypeDef(
        name = "estado_venta_credito",
        typeClass = PostgreSQLEnumType.class
)
public class VentaCredito implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "venta_id", nullable = true)
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_confirmacion")
    @Type(type = "tipo_confirmacion")
    private TipoConfirmacion tipoConfirmacion;

    private Integer cantidadCuotas;

    private Double valorTotal;

    private Double saldoTotal;

    private Integer plazoEnDias;

    private Float interesPorDia;

    private Float interesMoraDia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "estado_venta_credito")
    private EstadoVentaCredito estado;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



