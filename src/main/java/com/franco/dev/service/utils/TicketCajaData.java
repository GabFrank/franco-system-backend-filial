package com.franco.dev.service.utils;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketCajaData {
    private Sucursal sucursal;
    private Usuario usuario;
    private Venta venta;
    private List<VentaItem> ventaItemList;
    private Cliente cliente;
}
