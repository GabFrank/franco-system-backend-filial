package com.franco.dev.rabbit.dto;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "rabbitmq_msg", schema = "configuraciones")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class RabbitmqMsg implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipoAccion;

    private String tipoEntidad;

    @Type(type = "jsonb")
    @Column(name = "entidad", nullable = false)
    private Object entidad;

    private Long idSucursalOrigen;

    private String data;

    private Boolean recibidoEnServidor;

    private Boolean recibidoEnFilial;

    private String exchange;

    private String key;

    private Class<?> classType;
}
