package com.franco.dev.domain.financiero;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

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

    /** Leido por el lector del PDV desde el QR impreso en el cupon. */
    public static final String ORIGEN_QR = "QR";
    /** Extraido por el OCR de una foto del cupon. */
    public static final String ORIGEN_OCR = "OCR";
    /** Tipeado por el cajero. Es la salida universal: existe para todo tipo de terminal. */
    public static final String ORIGEN_MANUAL = "MANUAL";
    /** Traido de la API del proveedor. Todavia no hay ninguna integracion asi. */
    public static final String ORIGEN_API = "API";

    /**
     * Los cuatro validos, en el mismo orden que el CHECK de la columna (V97.5).
     * <p>
     * Existe para poder validar ANTES de guardar: si el valor llega hasta el INSERT, la violacion
     * del CHECK sube como DataIntegrityViolationException y el cajero ve un error opaco.
     */
    public static final java.util.List<String> ORIGENES = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(ORIGEN_QR, ORIGEN_OCR, ORIGEN_MANUAL, ORIGEN_API));

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

    /**
     * Moneda del COBRO que este registro respalda, no la de la terminal.
     *
     * Sin esto, monto y monto_escaneado no tienen unidad: la lista los pintaba con la moneda
     * actual de la terminal, asi que cambiar esa configuracion reescribia el significado de todo
     * el historico. Y en la conciliacion, 8.000 R$ contra 8.000 Gs daba diferencia cero.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;


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

    /**
     * Campos del cupon que no son canonicos, como clave-valor.
     *
     * El mapeo del formato decide que valor extraido va a monto, codigo_autorizacion,
     * numero_boleta y terminal; todo lo demas cae aca. Asi un proveedor nuevo con campos
     * propios se resuelve desde el ABM: sin codigo, sin migracion y sin propagar a 24
     * filiales. PlugPay es el caso que lo motivo: imprime dos montos en dos monedas y cual
     * es el de la venta es configuracion, no una constante del sistema.
     */
    @Column(name = "datos_extra", columnDefinition = "jsonb")
    @Type(type = "com.vladmihalcea.hibernate.type.json.JsonBinaryType")
    private String datosExtra;

    /**
     * Cadena cruda que entro por el lector cuando el registro se completo escaneando
     * el QR del cupon. Se guarda sin normalizar: es la unica evidencia para diagnosticar
     * un cupon que parseo mal despues de que el ticket termico se borro.
     * Queda NULL cuando el registro se completo por la app movil (foto + OCR).
     */
    @Column(name = "qr_crudo", length = 512)
    private String qrCrudo;

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    /**
     * De donde salieron los datos del cupon: {@link #ORIGEN_QR}, {@link #ORIGEN_OCR},
     * {@link #ORIGEN_MANUAL} o {@link #ORIGEN_API}.
     * <p>
     * No todos los origenes merecen la misma confianza: un codigo leido por OCR puede tener un
     * caracter mal --el {@code Cargo: 002511} leido {@code 802511} lo fallan los dos motores--,
     * uno tipeado por un cajero puede tener cualquier cosa, y uno que viene de la API del
     * proveedor no puede estar mal. Sin esta columna, la conciliacion no puede responder la unica
     * pregunta que importa: cuales de estas filas necesitan que las mire una persona.
     * <p>
     * Es el origen <b>dominante</b> de la fila, no uno por campo: si el cajero corrige a mano un
     * campo que el OCR leyo mal, la fila es MANUAL. Lo que se quiere saber es si hubo
     * intervencion humana, no la genealogia de cada dato.
     * <p>
     * NULL en las filas anteriores a la columna: historico desconocido, sin backfill. Un 'QR'
     * inventado mentiria; el NULL dice la verdad.
     */
    @Column(length = 20)
    private String origen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
}
