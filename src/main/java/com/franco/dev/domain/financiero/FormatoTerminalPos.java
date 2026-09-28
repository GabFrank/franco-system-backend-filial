package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Como se lee el ticket de un MODELO DE APARATO.
 * <p>
 * Reemplaza a {@link FormatoQrPos}, que resolvia el formato por proveedor y no distinguia una
 * maquinita Bancard de un portal web Bancard, ni dos firmwares de la misma marca. Aca un
 * proveedor tiene tantos formatos como modelos tenga, y cada terminal elige el suyo.
 * <p>
 * En el filial es SOLO LECTURA: la fila se administra en el central y baja por replicacion
 * MAIN_TO_ALL (V95.5 espejo aca). No hay mutation en este repo a proposito — un formato editable
 * desde una sucursal se desincronizaria del resto de la flota en cuanto alguien lo tocara.
 * <p>
 * El PDV la necesita local porque el escaneo del cupon tiene que funcionar sin internet, igual
 * que el resto del flujo de venta con tarjeta.
 *
 * @see com.franco.dev.graphql.financiero.FormatoTerminalPosGraphQL
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "formato_terminal_pos", schema = "financiero")
public class FormatoTerminalPos implements Serializable {

    private static final long serialVersionUID = 1L;

    /** MAQUINA imprime un ticket que hay que fotografiar: camara + OCR, nunca lector. */
    public static final String TIPO_MAQUINA = "MAQUINA";
    /** WEB imprime un QR con los datos ya estructurados: lector, nunca camara. */
    public static final String TIPO_WEB = "WEB";
    /** API: los campos llegan del proveedor. Entra al modelo pero el ABM todavia no lo ofrece. */
    public static final String TIPO_API = "API";

    @Id
    private Long id;

    /**
     * Obligatorio como regla de negocio, pero la columna es nullable: la valida el ABM del
     * central, que es quien escribe. Poner {@code nullable = false} aca seria mentir sobre el
     * esquema --y con {@code ddl-auto=none} Hibernate ni siquiera lo verifica. Ver V95.5.
     */
    @Column(length = 100)
    private String nombre;

    /**
     * Proveedor al que pertenece el formato. NULL = comodin: se prueba cuando la terminal no
     * tiene formato propio asignado.
     */
    @Column(name = "proveedor_servicio_id")
    private Long proveedorServicioId;

    /**
     * MAQUINA | WEB | API. Es el router del flujo: decide que camino se le ofrece al cajero y,
     * mas importante, cual se le CIERRA.
     * <p>
     * String y no un enum de Java: {@code venta_tarjeta.estado} ya es String en este modulo, y en
     * PostgreSQL la columna es VARCHAR sin CHECK, porque un CHECK en el subscriber puede abortar
     * el apply cuando el central agrega un valor que esta filial todavia no conoce. Ver V95.5.
     */
    @Column(length = 20)
    private String tipo = TIPO_MAQUINA;

    /**
     * Regex con grupos nombrados, anclado con ^ y $.
     * <p>
     * NULL solo para {@link #TIPO_API}. Para MAQUINA y WEB es obligatorio: el OCR devuelve texto
     * igual que el QR y se matchea con el mismo patron.
     */
    @Column(columnDefinition = "text")
    private String patron;

    /**
     * JSON: campo destino -> {de: grupo, obligatorio: bool, mapa/escala/escalaSegunMoneda/
     * formato+zona/mayusculas}.
     * <p>
     * Los campos marcados obligatorios deciden tres cosas de una vez: que tiene que encontrar el
     * OCR para que la operacion no falle, cuando el resultado es utilizable, y que campos pide el
     * formulario de carga a mano.
     */
    @Column(columnDefinition = "text")
    private String mapeo;

    /** Cadena real de ejemplo; el ABM del central no deja guardar si el patron no la matchea. */
    @Column(columnDefinition = "text")
    private String ejemplo;

    /**
     * Ojo con el significado: {@code false} es <b>"no elegible para asignar a terminales
     * nuevas"</b>, no "deja de funcionar". Las terminales que ya lo tienen asignado siguen
     * operando — si desactivar un formato apagara las terminales que le apuntan, un clic en el
     * ABM del central dejaria sucursales enteras sin poder vender con tarjeta.
     */
    @Column
    private Boolean activo = true;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    /**
     * Un formato de maquinita: el cupon se fotografia y lo lee el OCR.
     * <p>
     * MAQUINA es el default del dominio, asi que un {@code tipo} nulo --fila replicada a medias--
     * cuenta como maquinita. Es el lado seguro: ofrece la camara, que sirve para cualquier cupon
     * de papel, en vez de cerrar los dos caminos por un dato faltante.
     */
    public boolean esMaquina() {
        return tipo == null || TIPO_MAQUINA.equals(tipo);
    }

    /** Un formato web: el cupon trae QR y lo lee el lector del PDV. */
    public boolean esWeb() {
        return TIPO_WEB.equals(tipo);
    }
}
