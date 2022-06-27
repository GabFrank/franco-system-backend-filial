package com.franco.dev.graphql.operaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaItemTicketData {
    String descripcion;
    String precioVenta;
    String valorTotal;
    String codigo;
    String cantidad;
    String iva;
}
