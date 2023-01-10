package com.franco.dev.rabbit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "rabbitmq_msg", schema = "configuraciones")
public class RabbitmqMsg implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipoAccion;

    private String tipoEntidad;

    private String entidad;

    private Long idSucursalOrigen;

    private String data;

    private Boolean recibidoEnServidor;

    private Boolean recibidoEnFilial;

    private String exchange;

    private String key;

    private Class<?> classType;
}