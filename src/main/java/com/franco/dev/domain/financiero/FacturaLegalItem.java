package com.franco.dev.domain.financiero;

import com.franco.dev.domain.operaciones.VentaItem;
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
@Table(name = "factura_legal_item", schema = "financiero")
public class FacturaLegalItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String timbrado;
    private String nroSucursal;
    private String nroFactura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_legal_id", nullable = true)
    private FacturaLegal facturaLegal;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_item_id", nullable = true)
    private VentaItem ventaItem;
    private Float cantidad;
    private String descripcion;
    private Double precioUnitario;
    private Double total;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



