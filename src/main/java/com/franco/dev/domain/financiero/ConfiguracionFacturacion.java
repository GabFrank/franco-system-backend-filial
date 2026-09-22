package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Politica de facturacion automatica (issue #127).
 * <p>
 * Read-only en filial: las filas llegan por replicacion logica desde el central (MAIN_TO_ALL).
 * {@code sucursalId} NULL es la politica global; con valor, el override de esa sucursal.
 * <p>
 * Todas las columnas son nullable en el espejo (V103.1), y Hibernate pisa los inicializadores con
 * lo que traiga la fila: nadie lee los campos crudos para decidir, lo hace
 * {@code ConfiguracionFacturacionLector}, que tolera NULLs y valores desconocidos.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_facturacion", schema = "financiero")
public class ConfiguracionFacturacion implements Identifiable<Long> {

    /** Toda venta con punto de venta se factura. */
    public static final String MODO_TODAS = "TODAS";
    /** Una de cada {@code ventasSinFactura + 1} ventas se factura: el facturaCountDown de siempre. */
    public static final String MODO_INTERVALO = "INTERVALO";
    /** Nunca se factura automaticamente: solo cuando el cliente la pide (F12, o Venta + Ticket). */
    public static final String MODO_A_PEDIDO = "A_PEDIDO";

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    /** Plano, sin {@code @ManyToOne}: el espejo no tiene FK a sucursal. */
    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Column(name = "modo", length = 20)
    private String modo;

    @Column(name = "ventas_sin_factura")
    private Integer ventasSinFactura;

    @Column(name = "venta_ticket_respeta_politica")
    private Boolean ventaTicketRespetaPolitica;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;
}
