package com.franco.dev.graphql.operaciones.publisher;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.general.Barrio;
import com.franco.dev.domain.operaciones.PrecioDelivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.Vuelto;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;

public class DeliveryUpdate {

    private Long id;

    private Venta venta;

    private Double valor;

    private Funcionario entregador;

    private Barrio barrio;


    private Integer vehiculo;


    private PrecioDelivery precio;

    private String telefono;

    private String direccion;


    private Cliente cliente;

    private FormaPago formaPago;

    private LocalDateTime creadoEn;

    private Usuario usuario;

    private Vuelto vuelto;

    private DeliveryEstado estado;
}
