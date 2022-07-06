package com.franco.dev.domain.restaurant;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.ProductoIngrediente;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pedido_item_ingredientes_res", schema = "restaurant")
//@TypeDef(
//        name = "pedido_res_estado",
//        typeClass = PostgreSQLEnumType.class
//)
public class PedidoItemIngredienteRes implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_ingrediente_id", nullable = true)
    private ProductoIngrediente productoIngrediente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_item_res_id", nullable = true)
    private PedidoItemRes pedidoItemRes;

    private Boolean adicionar;

    private Boolean costo;

    private Float cantidad;

    private Double precio;

    private String observacion;

    private LocalDate creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}