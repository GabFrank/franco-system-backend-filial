package com.franco.dev.domain.financiero;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Cliente;
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
@Table(name = "factura_legal", schema = "financiero")
public class FacturaLegal implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sucursalId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "timbrado_detalle_id", nullable = true)
    private TimbradoDetalle timbradoDetalle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "caja_id", nullable = true)
    private PdvCaja caja;

    private Boolean viaTributaria;

    private Boolean autoimpreso;

    @Column(name = "numeroFactura")
    private Integer numeroFactura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = true)
    private Venta venta;

    private LocalDateTime fecha;
    private Boolean credito;
    private String nombre;
    private String ruc;
    private String direccion;

    @Column(name = "iva_parcial_0")
    private Double ivaParcial0;
    @Column(name = "iva_parcial_5")
    private Double ivaParcial5;
    @Column(name = "iva_parcial_10")
    private Double ivaParcial10;
    @Column(name = "total_parcial_0")
    private Double totalParcial0;
    @Column(name = "total_parcial_5")
    private Double totalParcial5;
    @Column(name = "total_parcial_10")
    private Double totalParcial10;

    private Double totalFinal;

    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



