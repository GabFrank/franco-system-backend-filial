package com.franco.dev.graphql.productos.input;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.productos.Producto;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

@Data
public class CostoPorProductoInput {
    private Long id;
    private Long productoId;
    private Long sucursalId;
    private Long monedaId;
    private Long movimientoStockId;
    private Double ultimoPrecioCompra;
    private Double ultimoPrecioVenta;
    private Double costoMedio;
    private Float existencia;
    private Double cotizacion;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
