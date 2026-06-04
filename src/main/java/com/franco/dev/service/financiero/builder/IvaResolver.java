package com.franco.dev.service.financiero.builder;

import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolucion del IVA de un item de factura legal segun una unica politica
 * (centralizada). Reemplaza los defaults divergentes que existian en
 * FacturaLegalGraphQL, FacturaLegalApiService, FacturaService y los paths de
 * print de ticket.
 *
 * Prioridad de resolucion:
 *   1. iva explicito en el input (frontend nuevo)
 *   2. producto.iva si producto fue resuelto via productoId
 *   3. ventaItem.producto.iva si ventaItemId fue resuelto
 *   4. presentacion.producto.iva si presentacionId fue resuelto
 *   5. match por descripcion (UPPER+TRIM contra producto.descripcion / descripcionFactura).
 *      Si todos los matches comparten el mismo iva -> usar ese iva.
 *      Si difieren -> log warn (no asignar).
 *   6. log.warn + default 10 (legacy-friendly, nunca rechaza la factura)
 *
 * Sin strictMode. El backend es safety net del frontend: si llega null igual
 * factura, pero deja log para detectar regresiones del frontend.
 */
public final class IvaResolver {

    private IvaResolver() {
    }

    public static Integer resolveIva(
            Integer ivaInput,
            Producto producto,
            VentaItem ventaItem,
            Presentacion presentacion,
            String descripcion,
            List<Producto> descMatches,
            Logger log) {

        if (ivaInput != null) {
            return ivaInput;
        }

        if (producto != null && producto.getIva() != null) {
            return producto.getIva();
        }

        if (ventaItem != null && ventaItem.getProducto() != null && ventaItem.getProducto().getIva() != null) {
            return ventaItem.getProducto().getIva();
        }

        if (presentacion != null && presentacion.getProducto() != null && presentacion.getProducto().getIva() != null) {
            return presentacion.getProducto().getIva();
        }

        if (descMatches != null && !descMatches.isEmpty()) {
            Set<Integer> ivasDistintos = new HashSet<>();
            for (Producto p : descMatches) {
                if (p.getIva() != null) {
                    ivasDistintos.add(p.getIva());
                }
            }
            if (ivasDistintos.size() == 1) {
                Integer iva = ivasDistintos.iterator().next();
                log.warn("IVA resuelto por descripcion para item desc='{}' (consensus {} match): iva={}",
                        descripcion, descMatches.size(), iva);
                return iva;
            } else if (ivasDistintos.size() > 1) {
                log.warn("IVA ambiguo por descripcion para item desc='{}' ({} matches con ivas distintos: {}), default 10",
                        descripcion, descMatches.size(), ivasDistintos);
            }
        }

        log.warn("IVA no resoluble para item desc='{}' productoId={} ventaItemId={} presentacionId={}, default 10",
                descripcion,
                producto != null ? producto.getId() : null,
                ventaItem != null ? ventaItem.getId() : null,
                presentacion != null ? presentacion.getId() : null);
        return 10;
    }
}
