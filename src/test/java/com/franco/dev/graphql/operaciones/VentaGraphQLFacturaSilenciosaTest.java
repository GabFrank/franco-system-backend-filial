package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.service.financiero.builder.ParcialesCalculator;
import com.franco.dev.service.operaciones.AjusteCobro;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La factura silenciosa (venta sin ticket que la politica factura) tiene que decir lo que se cobro:
 * el precio del item que cobro el PDV y el descuento del cobro. Antes salia por el total bruto
 * (venta 80078: se cobraron 5.500, la factura 30044 dice 6.000).
 *
 * <p>El builder recalcula el total desde los items y el descuento, y descarta el totalFinal del
 * input: por eso las cuentas pasan por {@link ParcialesCalculator}, como en
 * {@code FacturaLegalBuilder}.
 */
public class VentaGraphQLFacturaSilenciosaTest {

    private static VentaItem item(long id, double cantidad, double precio, double precioLista, int iva) {
        Producto producto = new Producto();
        producto.setIva(iva);
        producto.setDescripcionFactura("PRODUCTO " + id);
        Presentacion presentacion = new Presentacion();
        presentacion.setId(id);
        presentacion.setProducto(producto);
        PrecioPorSucursal precioVenta = new PrecioPorSucursal();
        precioVenta.setPrecio(precioLista);
        VentaItem vi = new VentaItem();
        vi.setId(id);
        vi.setPresentacion(presentacion);
        vi.setCantidad(cantidad);
        vi.setPrecio(precio);
        vi.setPrecioVenta(precioVenta);
        vi.setValorDescuento(0.0);
        return vi;
    }

    private static AjusteCobro descuento(double valor) {
        CobroDetalle cd = new CobroDetalle();
        cd.setValor(valor);
        cd.setCambio(1.0);
        cd.setDescuento(true);
        return AjusteCobro.deDetalles(Collections.singletonList(cd));
    }

    private static AjusteCobro aumento(double valor) {
        CobroDetalle cd = new CobroDetalle();
        cd.setValor(valor);
        cd.setCambio(1.0);
        cd.setAumento(true);
        return AjusteCobro.deDetalles(Collections.singletonList(cd));
    }

    private static Venta venta(double totalGs) {
        Venta v = new Venta();
        v.setId(80078L);
        v.setTotalGs(totalGs);
        return v;
    }

    /** Lo que guarda el builder: parciales y total desde los items y el descuento. */
    private static double totalGuardado(FacturaLegalInput input, List<FacturaLegalItemInput> items) {
        List<ParcialesCalculator.ItemIvaTuple> tuples = new ArrayList<>();
        for (FacturaLegalItemInput fi : items) {
            tuples.add(new ParcialesCalculator.ItemIvaTuple(fi.getIva(), fi.getTotal()));
        }
        double desc = input.getDescuento() != null ? input.getDescuento() : 0.0;
        return ParcialesCalculator.calcular(tuples, desc).getTotalFinal();
    }

    @Test
    void conDescuentoLaFacturaDiceLoCobrado() {
        List<VentaItem> vis = Collections.singletonList(item(1, 1, 6000, 6000, 10));
        List<FacturaLegalItemInput> items = VentaGraphQL.itemsFacturaSilenciosa(vis);
        FacturaLegalInput input = VentaGraphQL.inputFacturaSilenciosa(venta(6000), items, 7L, false,
                descuento(500));

        assertEquals(500.0, input.getDescuento(), 0.001);
        assertEquals(5500.0, input.getTotalFinal(), 0.001);
        assertEquals(5500.0, totalGuardado(input, items), 0.5);
    }

    @Test
    void sinDescuentoQuedaElBruto() {
        List<FacturaLegalItemInput> items = VentaGraphQL.itemsFacturaSilenciosa(
                Arrays.asList(item(1, 2, 3000, 3000, 10), item(2, 1, 1000, 1000, 5)));
        FacturaLegalInput input = VentaGraphQL.inputFacturaSilenciosa(venta(7000), items, 7L, false,
                AjusteCobro.SIN_AJUSTE);

        assertEquals(0.0, input.getDescuento(), 0.001);
        assertEquals(7000.0, totalGuardado(input, items), 0.5);
    }

    @Test
    void elItemSeFacturaAlPrecioCobradoNoAlDeLista() {
        // 134 items de 2026 tienen venta_item.precio distinto de precio_por_sucursal.precio.
        List<FacturaLegalItemInput> items = VentaGraphQL.itemsFacturaSilenciosa(
                Collections.singletonList(item(1, 2, 4500, 5000, 10)));

        assertEquals(4500.0, items.get(0).getPrecioUnitario(), 0.001);
        assertEquals(9000.0, items.get(0).getTotal(), 0.001);
    }

    @Test
    void sinPrecioCobradoUsaElDeListaMenosElDescuentoDelItem() {
        VentaItem vi = item(1, 1, 0, 5000, 10);
        vi.setPrecio(null);
        vi.setValorDescuento(200.0);
        List<FacturaLegalItemInput> items = VentaGraphQL.itemsFacturaSilenciosa(Collections.singletonList(vi));

        assertEquals(4800.0, items.get(0).getPrecioUnitario(), 0.001);
    }

    @Test
    void descuentoQueCubreElTotalNoSeFactura() {
        // Llegaria a la IllegalArgumentException de SifenService dentro de la transaccion de la
        // venta, que la deja rollback-only: se perderia la venta ya cobrada.
        List<FacturaLegalItemInput> items = VentaGraphQL.itemsFacturaSilenciosa(
                Collections.singletonList(item(1, 1, 6000, 6000, 10)));

        assertThrows(GraphQLException.class, () -> VentaGraphQL.inputFacturaSilenciosa(venta(6000), items,
                7L, false, descuento(6000)));
    }

    @Test
    void elAumentoNoEntraEnLaFacturaSilenciosa() {
        // SIFEN no prorratea un descuento negativo: factura y DE quedarian con totales distintos.
        List<FacturaLegalItemInput> items = VentaGraphQL.itemsFacturaSilenciosa(
                Collections.singletonList(item(1, 1, 6000, 6000, 10)));
        FacturaLegalInput input = VentaGraphQL.inputFacturaSilenciosa(venta(6000), items, 7L, false,
                aumento(500));

        assertEquals(0.0, input.getDescuento(), 0.001);
        assertEquals(6000.0, totalGuardado(input, items), 0.5);
    }

    @Test
    void sinClienteVaSinNombre() {
        List<FacturaLegalItemInput> items = VentaGraphQL.itemsFacturaSilenciosa(
                Collections.singletonList(item(1, 1, 6000, 6000, 10)));
        FacturaLegalInput input = VentaGraphQL.inputFacturaSilenciosa(venta(6000), items, 7L, true,
                AjusteCobro.SIN_AJUSTE);

        assertEquals("SIN NOMBRE", input.getNombre());
        assertEquals("X", input.getRuc());
        assertEquals(80078L, input.getVentaId().longValue());
        assertEquals(7L, input.getUsuarioId().longValue());
        assertTrue(input.getCredito());
    }
}
