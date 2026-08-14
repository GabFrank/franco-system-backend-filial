package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Ledger hijo de {@link MovimientoStock}: desglosa cada movimiento agregado en las cantidades que
 * corresponden a cada lote.
 *
 * NO es una tabla de saldos. El stock por lote se deriva sumando este ledger, igual que el stock
 * normal se deriva de SUM(movimiento_stock.cantidad). Ver la vista operaciones.v_stock_lote.
 *
 * INVARIANTE: para un mismo movimiento_stock, SUM(hijas.cantidad) = padre.cantidad, siempre en
 * unidades base.
 *
 * En la filial las filas tienen dos orígenes: las entradas por compra bajan del central por
 * replicación (a nivel BD, sin pasar por JPA) y las salidas por venta se insertan acá.
 *
 * Nota PK: la tabla tiene PRIMARY KEY (id, sucursal_id) como en el central, pero esta entidad mapea
 * solo {@code id} como @Id a propósito, igual que {@code VentaTarjeta}. La base de cada filial
 * contiene un único sucursal_id (la publicación del central baja filtrada por sucursal), así que
 * {@code id} alcanza para identificar la fila, y así se puede reusar CrudService en vez de
 * reimplementar el CRUD a mano por usar @IdClass.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "movimiento_stock_lote", schema = "operaciones")
public class MovimientoStockLote implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * La secuencia local va de 2 en 2 desde un valor par (V81.3), así que la filial genera SIEMPRE
     * ids PARES y el central IMPARES. Es lo que evita la colisión de PK cuando las filas de la
     * filial suben por replicación. Por eso alcanza con IDENTITY y no hace falta calcular el id en
     * Java como sí hace el central.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    /**
     * Id del movimiento de stock agregado del que este registro es desglose. Se guarda como Long
     * y no como {@code @ManyToOne} para no arrastrar el movimiento entero en cada inserción de
     * venta, que es camino crítico del POS.
     */
    @Column(name = "movimiento_stock_id", nullable = false)
    private Long movimientoStockId;

    /**
     * Lote al que pertenece este movimiento. Es la fuente de verdad del vencimiento y del estado.
     * Va sin FK en la filial a propósito: el maestro y el ledger viajan por publicaciones distintas
     * y no hay garantía de orden entre ellas (ver V82.3).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lote_id")
    private Lote lote;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Informativo: en qué presentación se movió. La cantidad va igual en unidades base. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id")
    private Presentacion presentacion;

    /**
     * Desnormalizado a propósito: es inmutable y deja la fila legible aunque el maestro todavía no
     * haya replicado.
     */
    @Column(name = "numero_lote", nullable = false)
    private String numeroLote;

    /** Positivo para entradas (COMPRA), negativo para salidas (VENTA). En unidades base. */
    @Column(name = "cantidad", nullable = false)
    private Double cantidad;

    /** Id de venta_item / recepcion_mercaderia_item según el origen del movimiento. */
    @Column(name = "referencia")
    private Long referencia;

    /**
     * Espejo del estado del movimiento padre. La filial anula ventas con soft delete, así que acá
     * se apaga en vez de borrarse: el ON DELETE CASCADE solo cubre el borrado físico del central.
     */
    @Column(name = "estado", nullable = false)
    private Boolean estado = true;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
