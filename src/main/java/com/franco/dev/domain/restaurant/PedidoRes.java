package com.franco.dev.domain.restaurant;

import com.franco.dev.domain.operaciones.PrecioDelivery;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.restaurant.enums.PedidoResEstado;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pedido_res", schema = "restaurant")
@TypeDef(
        name = "pedido_res_estado",
        typeClass = PostgreSQLEnumType.class
)
public class PedidoRes implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    private Boolean retirarEnLocal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entregador_id", nullable = true)
    private Funcionario entregador;

    private Boolean agendado;

    @Column(name = "hora_agend")
    private LocalDate horaAgend;

    private String ubicacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "precio_delivery_id", nullable = true)
    private PrecioDelivery precioDelivery;

    private Double descuento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type( type = "pedido_res_estado")
    private PedidoResEstado estado;

    private Date creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}