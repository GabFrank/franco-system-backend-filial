package com.franco.dev.domain.productos;

import com.franco.dev.domain.personas.Proveedor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductoExistenciaCosto  {

    private Producto producto;
    private List<ExistenciaCostoPorSucursal> existenciaPorSucursalList;
    private Double precioUltimaCompra;
    private Double cantidadUltimaCompra;
    private Proveedor proveedor;

}