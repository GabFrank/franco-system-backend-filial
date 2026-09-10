package com.franco.dev.domain.financiero;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Sesion de captura de foto de cupon entre el desktop y el telefono del cajero.
 *
 * <p>El desktop pide una captura, muestra su token en un QR, y el telefono abre la pagina que
 * sirve este mismo filial y sube la imagen. Cuando el OCR termina, el desktop se entera por
 * subscription.
 *
 * <p><b>El token es la unica credencial.</b> El telefono no tiene login ni rol: el QR solo lo
 * puede mostrar un desktop que ya paso las puertas --caja abierta, rol de venta, flujo
 * habilitado--, asi que la autorizacion ya ocurrio antes del QR. Por eso es de un solo uso
 * ({@code usadoEn}), expira en minutos y esta atado a una caja.
 *
 * <p><b>Local del filial: no se replica.</b> Es estado efimero entre dos maquinas de la misma
 * sucursal. Ver el encabezado de {@code V94.5__captura_cupon.sql}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "captura_cupon", schema = "financiero")
public class CapturaCupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Espera la foto del telefono. */
    public static final String ESPERANDO = "ESPERANDO";
    /** La foto llego y el OCR esta corriendo. */
    public static final String PROCESANDO = "PROCESANDO";
    /** Hay texto leido. */
    public static final String LISTO = "LISTO";
    /** Algo fallo; el motivo esta en {@code error}. */
    public static final String ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, length = 64)
    private String token;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "caja_id", nullable = false)
    private Long cajaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = ESPERANDO;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    /** Lo que hace el uso unico. Una vez seteado, el token no sirve mas. */
    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    /** El texto crudo que devolvio el OCR, una linea por caja detectada. */
    @Column(name = "texto_ocr", columnDefinition = "text")
    private String textoOcr;

    /** Campos ya mapeados por el formato del proveedor. Vacio hasta que exista el mapeo. */
    @Column(name = "campos", columnDefinition = "jsonb")
    @Type(type = "com.vladmihalcea.hibernate.type.json.JsonBinaryType")
    private String campos;

    @Column(name = "ms_ocr")
    private Integer msOcr;

    /**
     * Fotos subidas para este token. El token solo se consume con un resultado bueno, asi que
     * una foto movida o un fallo del motor se reintentan sin volver a la caja a pedir otro QR.
     */
    @Column(name = "intentos", nullable = false)
    private Integer intentos = 0;

    /** Varianza del laplaciano que midio el telefono, para poder ajustar el umbral con datos. */
    @Column(name = "nitidez", precision = 10, scale = 2)
    private java.math.BigDecimal nitidez;

    @Column(name = "error", length = 500)
    private String error;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    public boolean estaVencida() {
        return expiraEn != null && LocalDateTime.now().isAfter(expiraEn);
    }

    public boolean yaSeUso() {
        return usadoEn != null;
    }
}
