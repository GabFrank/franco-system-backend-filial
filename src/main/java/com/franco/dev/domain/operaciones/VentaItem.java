package com.franco.dev.domain.operaciones;

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

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = true)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = true)
    private Producto producto;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id", nullable = true)
    private Presentacion presentacion;

    private Double cantidad;

    private Double existencia;

    @Column(name = "costo_unitario")
    private Double precioCosto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "precio_id", nullable = true)
    private PrecioPorSucursal precioVenta;

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


}