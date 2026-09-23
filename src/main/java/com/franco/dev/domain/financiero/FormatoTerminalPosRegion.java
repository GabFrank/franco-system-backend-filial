package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una region del mapa: donde encontrar un campo dentro del cupon de un formato dado.
 *
 * <p><b>Solo lectura en filial.</b> Las filas llegan del central por MAIN_TO_ALL; el ABM vive
 * alla. Aca se leen para asignar las cajas que devolvio el OCR a campos concretos.
 *
 * <p><b>El ancla es la etiqueta, no la coordenada.</b> Es la regla que sostiene todo el diseno.
 * Un mapa por coordenadas absolutas se rompe el dia que el proveedor agrega una linea al ticket,
 * y se rompen todos los mapas de ese modelo a la vez sin que nadie entienda por que. Anclado a
 * la etiqueta sobrevive: si {@code AUT:} se corrio para abajo, el valor sigue estando a su
 * derecha.
 *
 * <p>La geometria ({@link #x1}..{@link #y2}, normalizada 0..1) esta igual, pero como <b>pista
 * para acotar el reconocimiento</b>, no como verdad para asignar. Ese es el hibrido: la
 * geometria achica el trabajo del OCR, la etiqueta decide de quien es cada caja.
 *
 * <p><b>Cuelgan del formato, no de la terminal</b>, porque el formato es del modelo de aparato:
 * dos cajas con la misma maquinita comparten el mapa en vez de dibujarlo dos veces.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "formato_terminal_pos_region", schema = "financiero")
public class FormatoTerminalPosRegion implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    /** El valor esta a la derecha de la etiqueta, en el mismo renglon. Es el caso comun. */
    public static final String POSICION_DERECHA = "DERECHA";
    /** El valor esta en el renglon de abajo. */
    public static final String POSICION_ABAJO = "ABAJO";
    /** La etiqueta y el valor salieron en la misma caja: "AUT: 123456". */
    public static final String POSICION_DENTRO = "DENTRO";

    public static final String TIPO_TEXTO = "TEXTO";
    public static final String TIPO_NUMERO = "NUMERO";
    public static final String TIPO_FECHA = "FECHA";

    /** La derivo el sistema desde un cupon de muestra. La derivacion la puede volver a pisar. */
    public static final String ORIGEN_DERIVADA = "DERIVADA";
    /** La corrigio una persona. La derivacion NO la pisa sin confirmacion explicita. */
    public static final String ORIGEN_MANUAL = "MANUAL";

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Sin FK en la base y con {@code @NotFound(IGNORE)} por el mismo motivo que el resto de los
     * espejos: esta tabla y {@code formato_terminal_pos} bajan por streams de replicacion sin
     * garantia de orden entre ellos, y una FK convertiria un desfasaje de segundos en un corte.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "formato_terminal_pos_id", nullable = true)
    @NotFound(action = NotFoundAction.IGNORE)
    private FormatoTerminalPos formatoTerminalPos;

    /**
     * Destino del valor. Los canonicos son {@code MONTO}, {@code CODIGO_AUTORIZACION},
     * {@code NUMERO_BOLETA} y {@code TERMINAL}; cualquier otra clave cae en
     * {@code venta_tarjeta.datos_extra}, que es lo que permite que un proveedor con campos
     * propios se resuelva desde el ABM y no con una migracion.
     */
    @Column(name = "campo", length = 40)
    private String campo;

    /** La etiqueta impresa que ancla la region: "AUT:", "MONTO", "TERMINAL". */
    @Column(name = "etiqueta", length = 120)
    private String etiqueta;

    /** {@link #POSICION_DERECHA} / {@link #POSICION_ABAJO} / {@link #POSICION_DENTRO}. */
    @Column(name = "posicion", length = 20)
    private String posicion;

    /**
     * {@link #TIPO_TEXTO} / {@link #TIPO_NUMERO} / {@link #TIPO_FECHA}.
     * <p>
     * Un campo declarado NUMERO rechaza gratis un {@code 0i64} del OCR --el tipo de error que ni
     * Java ni Python evitan solos-- sin perseguir paridad entre motores.
     */
    @Column(name = "tipo", length = 20)
    private String tipo;

    @Column(name = "obligatorio")
    private Boolean obligatorio = false;

    @Column(name = "x1")
    private BigDecimal x1;

    @Column(name = "y1")
    private BigDecimal y1;

    @Column(name = "x2")
    private BigDecimal x2;

    @Column(name = "y2")
    private BigDecimal y2;

    /** {@link #ORIGEN_DERIVADA} o {@link #ORIGEN_MANUAL}. Decide si la derivacion la puede pisar. */
    @Column(name = "origen", length = 20)
    private String origen;

    @Column(name = "orden")
    private Integer orden = 0;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    /** Hay pista geometrica utilizable. Sin las cuatro coordenadas no se puede acotar nada. */
    public boolean tienePista() {
        return x1 != null && y1 != null && x2 != null && y2 != null;
    }
}
