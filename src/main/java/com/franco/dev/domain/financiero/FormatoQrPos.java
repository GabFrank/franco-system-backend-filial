package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Como se lee el QR que imprime la maquinita de un proveedor.
 * <p>
 * En el filial es SOLO LECTURA: la fila se administra en el central y baja por
 * replicacion MAIN_TO_ALL (V216.5 central / V91.5 espejo aca). No hay mutation
 * en este repo a proposito — un formato editable desde una sucursal se
 * desincronizaria del resto de la flota en cuanto alguien lo tocara.
 * <p>
 * El PDV la necesita local porque el escaneo del cupon tiene que funcionar sin
 * internet, igual que el resto del flujo de venta con tarjeta.
 *
 * @see com.franco.dev.graphql.financiero.FormatoQrPosGraphQL
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "formato_qr_pos", schema = "financiero")
public class FormatoQrPos implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    /**
     * Proveedor al que pertenece el formato. NULL = comodin: se prueba cuando la
     * terminal escaneada no tiene proveedor asignado, o cuando el proveedor no
     * tiene formato propio.
     */
    @Column(name = "proveedor_servicio_id")
    private Long proveedorServicioId;

    /** Regex con grupos nombrados, anclado con ^ y $. */
    @Column(nullable = false, columnDefinition = "text")
    private String patron;

    /** JSON: campo destino -> {de: grupo, mapa/escala/escalaSegunMoneda/formato+zona/mayusculas}. */
    @Column(nullable = false, columnDefinition = "text")
    private String mapeo;

    /** Cadena real de ejemplo; el ABM del central no deja guardar si el patron no la matchea. */
    @Column(nullable = false, columnDefinition = "text")
    private String ejemplo;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
