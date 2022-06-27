package com.franco.dev.domain.financiero;

import com.franco.dev.domain.general.Pais;
import com.franco.dev.domain.personas.Usuario;
import com.sun.org.apache.xpath.internal.operations.Bool;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "forma_pago", schema = "financiero")
public class FormaPago implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String descripcion;

    private Boolean activo;

    @Column(name = "movimiento_caja")
    private Boolean movimientoCaja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_bancaria_id", nullable = true)
    private CuentaBancaria cuentaBancaria;

    private Boolean autorizacion;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



