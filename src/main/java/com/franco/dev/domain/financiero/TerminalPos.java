package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.ProveedorServicio;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "terminal_pos", schema = "financiero")
public class TerminalPos implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(
            name = "assigned-identity",
            strategy = "com.franco.dev.config.AssignedIdentityGenerator"
    )
    @GeneratedValue(
            generator = "assigned-identity",
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    private String descripcion;

    private String codigo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_bancaria_id", nullable = true)
    private CuentaBancaria cuentaBancaria;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    /**
     * La tabla espejo personas.proveedor_servicio no tiene FK a proposito (ver V81.1):
     * durante el initial sync las tablas se copian en paralelo, asi que terminal_pos puede
     * llegar antes que el proveedor al que apunta. Sin @NotFound(IGNORE) Hibernate tiraria
     * EntityNotFoundException y voltearia toda la consulta de terminales.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proveedor_servicio_id", nullable = true)
    @NotFound(action = NotFoundAction.IGNORE)
    private ProveedorServicio proveedorServicio;

    /**
     * Formato del modelo de aparato que es esta terminal: de aca sale el tipo (MAQUINA / WEB /
     * API), el patron y el mapeo.
     * <p>
     * NULL = sin configurar. El dia del corte lo estan TODAS las terminales de las 24 sucursales
     * --no hay backfill, la asignacion se completa a mano por SQL-- y el desktop bloquea la venta
     * con tarjeta mientras siga asi.
     * <p>
     * Sin FK en la base y con {@code @NotFound(IGNORE)} por el mismo motivo que
     * {@link #proveedorServicio}: terminal_pos y formato_terminal_pos bajan por dos streams de
     * replicacion sin garantia de orden entre ellos, y una FK convertiria un desfasaje de
     * segundos en un corte. Ver V95.5.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "formato_terminal_pos_id", nullable = true)
    @NotFound(action = NotFoundAction.IGNORE)
    private FormatoTerminalPos formatoTerminalPos;

    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
