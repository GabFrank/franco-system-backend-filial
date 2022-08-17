package com.franco.dev.domain.empresarial;

import com.franco.dev.domain.personas.Usuario;
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
@Table(name = "punto_de_venta", schema = "empresarial")
public class PuntoDeVenta implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String nombre;

    private String nombreImpresoraTicket;
    private Integer tamanhoImpresoraTicket;
    private Integer nombreImpresoraReportes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    private Date creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}