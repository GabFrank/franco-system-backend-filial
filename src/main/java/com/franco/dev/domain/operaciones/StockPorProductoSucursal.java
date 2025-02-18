package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "stock_por_producto_sucursal", schema = "operaciones")
public class StockPorProductoSucursal {

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Id
    @Column(name = "producto_id")
    private Long id;

    private Long lastMovimientoStockId;

    private Double cantidad;

    public Double sumarCantidad(Double aux){
        Double newCantidad = cantidad + aux;
        cantidad = newCantidad;
        return cantidad;
    }
}