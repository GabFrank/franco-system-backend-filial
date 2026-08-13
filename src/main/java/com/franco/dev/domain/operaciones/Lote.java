package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maestro de lotes. La unicidad es {@code (producto, numeroLote)}.
 *
 * SOLO LECTURA EN LA FILIAL. La tabla se administra exclusivamente en el central (replicación
 * MAIN_TO_ALL) porque los lotes nacen en la recepción de mercadería, que solo ocurre allá, y el
 * cambio de estado por recall también. Acá se necesita para dos cosas: ordenar FEFO por la fecha
 * de retiro y respetar el bloqueo de los lotes que no están LIBERADO.
 *
 * No escribir esta entidad desde la filial: un INSERT o UPDATE local chocaría contra las filas que
 * bajan por replicación.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "lote", schema = "operaciones")
public class Lote implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "numero_lote", nullable = false)
    private String numeroLote;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    /**
     * Fecha por la que ordena FEFO, no el vencimiento: la idea es sacar la mercadería antes de que
     * efectivamente venza. Se calcula en el central; puede venir cargada a mano en la recepción o
     * derivada de {@code producto.diasVencimiento}.
     */
    @Column(name = "fecha_retiro")
    private LocalDate fechaRetiro;

    @Column(name = "fecha_fabricacion")
    private LocalDate fechaFabricacion;

    /**
     * Se mapea como id suelto y no como {@code @ManyToOne}: la filial quitó la FK a proveedor
     * (V83.3) porque el maestro de lotes y el de proveedores viajan por publicaciones distintas y
     * no hay garantía de orden entre ellas. Un lote puede llegar antes que su proveedor.
     */
    @Column(name = "proveedor_id")
    private Long proveedorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoLote estado = EstadoLote.LIBERADO;

    @Column(name = "observacion")
    private String observacion;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}
