package com.franco.dev.domain.operaciones;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.enums.UnidadMedida;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import com.franco.dev.domain.operaciones.dto.LotePreferidoDto;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "venta_item", schema = "operaciones")
@TypeDef(
        name = "unidad_medida",
        typeClass = PostgreSQLEnumType.class
)
public class VentaItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = true)
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = true)
    private Producto producto;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "presentacion_id", nullable = true)
    private Presentacion presentacion;

    private Double cantidad;

    private Double existencia;

    @Column(name = "costo_unitario")
    private Double precioCosto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "precio_id", nullable = true)
    private PrecioPorSucursal precioVenta;

    private Double precio;

    @Column(name = "descuento_unitario")
    private Double valorDescuento;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida")
    @Type( type = "unidad_medida")
    private UnidadMedida unidadMedida;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean activo;

    // Rastro de la conversion del precio de lista (V110.5). precio sigue en la moneda en que se cobro.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "precio_moneda_id", nullable = true)
    private Moneda precioMoneda;

    @Column(name = "precio_lista")
    private BigDecimal precioLista;

    /** Unidades de la moneda de cobro por unidad de precioMoneda. */
    @Column(name = "precio_cotizacion")
    private BigDecimal precioCotizacion;

    /** Precio unitario cobrado en la moneda de la venta (V111.5); precio es su equivalente en la de la empresa. */
    @Column(name = "precio_en_moneda")
    private BigDecimal precioEnMoneda;

    /**
     * Lotes que el cajero eligió a mano en el POS. NO se persiste: viaja desde el resolver hasta
     * {@code VentaLoteService}, que lo usa para sesgar FEFO y después lo descarta. El registro de
     * qué salió de cada lote queda en el ledger operaciones.movimiento_stock_lote, no acá.
     *
     * Va como campo transiente y no como parámetro de save() porque save() se llama desde varios
     * lugares y cambiarle la firma obligaría a tocarlos todos solo para pasar null.
     *
     * Null o vacío = FEFO puro, que es el camino de casi todas las ventas.
     */
    @Transient
    private List<LotePreferidoDto> lotesPreferidos;
}