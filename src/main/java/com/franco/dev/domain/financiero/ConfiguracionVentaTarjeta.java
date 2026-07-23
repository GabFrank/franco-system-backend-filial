package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Read-only en filial: la fila unica llega por replicacion logica desde el
 * central (MAIN_TO_ALL). Filial nunca crea ni modifica esta configuracion.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_venta_tarjeta", schema = "financiero")
public class ConfiguracionVentaTarjeta implements Identifiable<Long> {

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "habilitado", nullable = false)
    private Boolean habilitado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;
}
