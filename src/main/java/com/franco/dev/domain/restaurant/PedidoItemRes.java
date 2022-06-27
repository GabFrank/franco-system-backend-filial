package com.franco.dev.domain.restaurant;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pedido_item_res", schema = "restaurant")
//@TypeDef(
//        name = "pedido_res_estado",
//        typeClass = PostgreSQLEnumType.class
//)
public class PedidoItemRes implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = true)
    private PedidoRes pedidoRes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = true)
    private Producto producto;

    private Float cantidad;

    @Column(name = "precio_unitario")
    private Double precioUnitario;

    @Column(name = "descuento_unitario")
    private Double descuentoUnitario;

    @Column(name = "precio_costo")
    private Double precioCosto;

    private String observacion;

    private Date creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}