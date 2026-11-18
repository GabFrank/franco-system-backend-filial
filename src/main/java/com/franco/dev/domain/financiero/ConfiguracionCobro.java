package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only en filial: la fila unica llega por replicacion logica desde el central (MAIN_TO_ALL).
 * <p>
 * Las columnas son nullable en la base (es el subscriber), y Hibernate pisa el inicializador del
 * field con lo que traiga la fila. Por eso los getters de los dos campos del aumento nunca
 * devuelven NULL: un dato faltante se lee como el comportamiento de antes (con tope del 20%).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_cobro", schema = "financiero")
public class ConfiguracionCobro implements Identifiable<Long> {

    /** El 20% que estaba clavado en el boton de aumento del desktop. */
    public static final BigDecimal TOPE_DEFAULT = new BigDecimal("20");

    /** El que propone el central al activar el tope del descuento (V272.3). */
    public static final BigDecimal TOPE_DESCUENTO_DEFAULT = new BigDecimal("15");

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aumento_con_tope")
    private Boolean aumentoConTope = true;

    @Column(name = "aumento_tope_porcentaje")
    private BigDecimal aumentoTopePorcentaje = TOPE_DEFAULT;

    @Column(name = "descuento_con_tope")
    private Boolean descuentoConTope = false;

    @Column(name = "descuento_tope_porcentaje")
    private BigDecimal descuentoTopePorcentaje = TOPE_DESCUENTO_DEFAULT;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;

    public Boolean getAumentoConTope() {
        return aumentoConTope == null ? Boolean.TRUE : aumentoConTope;
    }

    /** Un porcentaje no positivo no tiene sentido como tope: se cae al default, no a "sin tope". */
    public BigDecimal getAumentoTopePorcentaje() {
        return aumentoTopePorcentaje == null || aumentoTopePorcentaje.signum() <= 0
                ? TOPE_DEFAULT : aumentoTopePorcentaje;
    }

    /** Al reves que el aumento: un dato faltante es "sin tope", que es como funcionaba el descuento antes. */
    public Boolean getDescuentoConTope() {
        return descuentoConTope == null ? Boolean.FALSE : descuentoConTope;
    }

    public BigDecimal getDescuentoTopePorcentaje() {
        return descuentoTopePorcentaje == null || descuentoTopePorcentaje.signum() <= 0
                ? TOPE_DESCUENTO_DEFAULT : descuentoTopePorcentaje;
    }
}
