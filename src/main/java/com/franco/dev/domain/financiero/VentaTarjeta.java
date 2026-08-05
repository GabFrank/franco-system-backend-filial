package com.franco.dev.domain.financiero;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro de venta con tarjeta creado por el POS en el filial (estado PENDIENTE)
 * y replicado BRANCH_TO_MAIN al central. El estado COMPLETADO que setea la app
 * movil en el central vuelve por replicacion central->filial filtrada por sucursal.
 * Estados: PENDIENTE, COMPLETADO, CANCELADO, NO_COMPLETADO.
 * <p>
 * Nota PK: la tabla tiene PRIMARY KEY (id, sucursal_id) (espejo del central),
 * pero esta entity mapea solo {@code id} como @Id a proposito: el filial es el
 * unico que inserta (BIGSERIAL local, id unico tras el setval de despliegue),
 * el backflow del central llega por replicacion logica a nivel BD (no pasa por
 * JPA), y asi se puede reusar CrudService (tipado a Long, cf. MarcacionService
 * que debio reimplementar CRUD a mano por usar @IdClass).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "venta_tarjeta", schema = "financiero")
public class VentaTarjeta implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "venta_id", nullable = false)
    private Long ventaId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "terminal_pos_id", nullable = true)
    private TerminalPos terminalPos;

    @Column(name = "caja_id", nullable = false)
    private Long cajaId;

    @Column(name = "codigo_autorizacion")
    private String codigoAutorizacion;

    @Column(name = "numero_boleta")
    private String numeroBoleta;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "monto_escaneado", precision = 18, scale = 2)
    private BigDecimal montoEscaneado;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
}
