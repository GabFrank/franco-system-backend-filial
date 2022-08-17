package com.franco.dev.domain.financiero;

import com.franco.dev.domain.empresarial.PuntoDeVenta;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "timbrado_detalle", schema = "financiero")
public class TimbradoDetalle implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "timbrado_id", nullable = true)
    private Timbrado timbrado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "punto_de_venta_id", nullable = true)
    private PuntoDeVenta puntoDeVenta;

    private String puntoExpedicion;

    private Long cantidad;

    private Long rangoDesde;

    private Long rangoHasta;

    private Long numeroActual;

    private Boolean activo;


    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



