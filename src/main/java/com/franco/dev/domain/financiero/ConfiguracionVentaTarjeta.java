package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only en filial: la fila unica llega por replicacion logica desde el
 * central (MAIN_TO_ALL). Filial nunca crea ni modifica esta configuracion.
 * <p>
 * Los campos nuevos son nullable en la base a proposito: en un subscriber, un NOT NULL o un CHECK
 * que el publisher no comparta convierte un valor legitimo en un corte de replicacion. Los
 * defaults viven aca y en {@code ConfiguracionVentaTarjetaService.findOrDefault()}, para que un
 * filial que todavia no recibio la fila se comporte exactamente como hoy.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_venta_tarjeta", schema = "financiero")
public class ConfiguracionVentaTarjeta implements Identifiable<Long> {

    /** El cajero puede dejar la venta en PENDIENTE y cerrar la caja igual. Es lo de hoy. */
    public static final String REGISTRO_LIBRE = "LIBRE";
    /** Se avisa al cerrar, pero no frena. */
    public static final String REGISTRO_AVISA = "AVISA_AL_CERRAR";
    /** No se puede cerrar la caja con ventas con tarjeta sin registrar. */
    public static final String REGISTRO_BLOQUEA = "BLOQUEA_EL_CIERRE";

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "habilitado", nullable = false)
    private Boolean habilitado = false;

    /**
     * Que pasa al cerrar la caja con ventas con tarjeta en PENDIENTE:
     * {@link #REGISTRO_LIBRE}, {@link #REGISTRO_AVISA} o {@link #REGISTRO_BLOQUEA}.
     * <p>
     * Es el campo que mas cambia el comportamiento del modulo. Hoy "Registrar mas tarde" deja la
     * venta en PENDIENTE y <b>nada la persigue</b>: el cierre de caja no las mira. La razon de ser
     * del modulo depende de que el cajero se acuerde.
     * <p>
     * El default es LIBRE, que es lo de hoy: migrar y que de golpe el cierre empiece a bloquear
     * seria un cambio de comportamiento silencioso en toda la flota.
     */
    @Column(name = "registro_obligatorio", length = 20)
    private String registroObligatorio = REGISTRO_LIBRE;

    /**
     * Porcentaje por debajo del cual la diferencia entre el cupon y el cobro no pide
     * confirmacion. 0 = confirmar siempre, que es lo de hoy.
     * <p>
     * <b>Porcentaje y no monto</b>: {@code monto} y {@code monto_escaneado} se guardan sin unidad,
     * asi que un umbral absoluto no sabria en que moneda esta. Un cupon de 8.000 R$ contra un
     * cobro de 8.000 Gs da diferencia cero y son ~5900x — ver
     * {@code VentaTarjetaService.validarMoneda}.
     */
    @Column(name = "tolerancia_diferencia_monto_pct")
    private BigDecimal toleranciaDiferenciaMontoPct = BigDecimal.ZERO;

    /** Vida del token del QR de captura, en minutos. Lo lee {@code CapturaCuponService}. */
    @Column(name = "minutos_validez_captura")
    private Integer minutosValidezCaptura = 10;

    /** Countdown del dialogo de registro en el desktop, en segundos. */
    @Column(name = "segundos_dialogo_registro")
    private Integer segundosDialogoRegistro = 120;

    /** Cuanto atras mira el chequeo de cupon duplicado por codigo de autorizacion, en horas. */
    @Column(name = "horas_ventana_duplicado")
    private Integer horasVentanaDuplicado = 24;

    /**
     * Dias de retencion de las fotos de cupon. NULL = no purgar.
     * <p>
     * Sin lector todavia: el job de purga es de la etapa 6. La columna se adelanto porque es mas
     * barato que coordinar un segundo despliegue filial-primero sobre 24 filiales.
     */
    @Column(name = "dias_retencion_imagenes")
    private Integer diasRetencionImagenes;

    /**
     * Umbral de espacio libre que dispara alerta, en MB. NULL = sin alerta.
     * <p>
     * No es un lujo: las imagenes, {@code releases/} y los datos de PostgreSQL comparten disco en
     * un filial. Un disco lleno no solo rompe el guardado de fotos — impide que Postgres escriba
     * WAL y tumba todas las ventas de esa sucursal.
     */
    @Column(name = "mb_libres_minimos")
    private Integer mbLibresMinimos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;

    // ── Lecturas seguras ─────────────────────────────────────────────────────────────────────
    //
    // Los campos son nullable en la base (ver el javadoc de la clase), y ademas Hibernate pisa el
    // inicializador del field con lo que traiga la fila. Asi que una fila replicada con NULL
    // llega aca como NULL, no como el default. Todo consumidor usa estos getters, no el crudo.

    /** LIBRE si no vino nada: nunca endurecer el comportamiento por un dato faltante. */
    public String registroObligatorioEfectivo() {
        return registroObligatorio != null ? registroObligatorio : REGISTRO_LIBRE;
    }

    /** El cierre de caja frena solo si esta explicitamente configurado para frenar. */
    public boolean bloqueaElCierre() {
        return REGISTRO_BLOQUEA.equals(registroObligatorioEfectivo());
    }

    /** 0 = confirmar toda diferencia, que es el comportamiento anterior a esta columna. */
    public BigDecimal toleranciaDiferenciaMontoPctEfectiva() {
        return toleranciaDiferenciaMontoPct != null ? toleranciaDiferenciaMontoPct : BigDecimal.ZERO;
    }

    /** Los 10 minutos que estaban clavados en CapturaCuponService.MINUTOS_VALIDEZ. */
    public int minutosValidezCapturaEfectivo() {
        return minutosValidezCaptura != null && minutosValidezCaptura > 0 ? minutosValidezCaptura : 10;
    }

    /**
     * Horas hacia atras del chequeo de cupon duplicado.
     * <p>
     * Un 0 o un negativo apagaria el chequeo entero, que es justo lo que no puede pasar por un
     * dato mal cargado: se cae al default.
     */
    public int horasVentanaDuplicadoEfectivo() {
        return horasVentanaDuplicado != null && horasVentanaDuplicado > 0 ? horasVentanaDuplicado : 24;
    }
}
