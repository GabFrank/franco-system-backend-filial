package com.franco.dev.domain.productos;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.personas.Proveedor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExistenciaCostoPorSucursal {

    private Sucursal sucursal;
    private Float existencia;
    private Double precioUltimaCompra;
    private Double cantidadUltimaCompra;
    private Double costoMedio;
    private Pedido pedido;
    private LocalDateTime fechaUltimaCompra;
    private Float cantMinima;
    private Float cantMedia;
    private Float cantMaxima;
    private Moneda moneda;
}