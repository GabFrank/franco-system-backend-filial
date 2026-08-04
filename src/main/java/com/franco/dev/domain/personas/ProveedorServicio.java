package com.franco.dev.domain.personas;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.CuentaBancaria;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Proveedor de servicios: la empresa que provee las terminales POS y su soporte tecnico.
 *
 * Espejo de solo lectura del central. Las filas llegan por replicacion logica MAIN_TO_ALL;
 * el ABM vive en el central y aca no se expone ninguna Query ni Mutation. La tabla local
 * no tiene FKs a proposito (ver V81.1), asi que las relaciones de abajo pueden quedar
 * apuntando a filas que todavia no terminaron de replicarse.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "proveedor_servicio", schema = "personas")
public class ProveedorServicio implements Identifiable<Long> {

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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "persona_id", nullable = true)
    private Persona persona;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_bancaria_id", nullable = true)
    private CuentaBancaria cuentaBancaria;

    @Column(name = "nombre_contacto")
    private String nombreContacto;

    @Column(name = "numero_contacto")
    private String numeroContacto;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
