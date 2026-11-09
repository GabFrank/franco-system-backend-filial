package com.franco.dev.eventos;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Catalogo de los eventos con proyeccion (fase 2): por tipo, la familia, los atributos por los que una
 * suscripcion puede filtrar y la lista blanca cerrada de rutas del `data`, cada una con su clase.
 *
 * Las rutas van con puntos y sin indices: "cobro.cobroDetalleList.valor" vale para cada elemento de la
 * lista. Lo que no esta en la lista no sale nunca (Proyeccion.limpiar). PERSONAL sale solo con
 * datos_personales; SENSIBLE solo con datos_sensibles.
 *
 * Una suscripcion o un permiso nuevos valen para lo que se emite desde ese momento: el outbox guarda lo
 * personal y lo sensible solo si al emitir alguna suscripcion de ese tipo los tenia habilitados.
 */
public final class CatalogoEventos {

    public enum Clase { OPERATIVO, PERSONAL, SENSIBLE }

    public enum Familia { CAMBIO, ALERTA }

    public static final class Tipo {
        public final String nombre;
        public final Familia familia;
        public final Set<String> atributos;
        public final Map<String, Clase> rutas;

        Tipo(String nombre, Familia familia, Set<String> atributos, Map<String, Clase> rutas) {
            this.nombre = nombre;
            this.familia = familia;
            this.atributos = Collections.unmodifiableSet(atributos);
            this.rutas = Collections.unmodifiableMap(rutas);
        }
    }

    private static final Map<String, Tipo> TIPOS = new LinkedHashMap<>();

    private static final Clase O = Clase.OPERATIVO;
    private static final Clase P = Clase.PERSONAL;
    private static final Clase S = Clase.SENSIBLE;

    static {
        // ---------------------------------------------------------------- venta
        Map<String, Clase> venta = rutas(
                "id", O, "estado", O, "creadoEn", O, "sucursalId", O,
                "totalGs", O, "totalRs", O, "totalDs", O, "valorTotal", O, "valorDescuento", O,
                "esDelivery", O, "ventaObs", P,
                "usuario.id", O, "usuario.nickname", P,
                "cliente.id", O, "cliente.codigo", O, "cliente.nombre", P, "cliente.persona.nombre", P,
                "caja.id", O, "caja.fechaApertura", O, "caja.fechaCierre", O, "caja.usuario.nickname", P,
                "cobro.id", O,
                "cobro.cobroDetalleList.id", O, "cobro.cobroDetalleList.valor", O, "cobro.cobroDetalleList.cambio", O,
                "cobro.cobroDetalleList.pago", O, "cobro.cobroDetalleList.vuelto", O,
                "cobro.cobroDetalleList.descuento", O, "cobro.cobroDetalleList.aumento", O,
                "cobro.cobroDetalleList.identificadorTransaccion", S,
                "cobro.cobroDetalleList.moneda.denominacion", O, "cobro.cobroDetalleList.formaPago.descripcion", O);
        Map<String, Clase> ventaConItems = new LinkedHashMap<>(venta);
        ventaConItems.putAll(rutas(
                "ventaItemList.id", O, "ventaItemList.cantidad", O, "ventaItemList.precio", O,
                "ventaItemList.valorDescuento", O, "ventaItemList.valorTotal", O,
                "ventaItemList.producto.id", O, "ventaItemList.producto.descripcion", O,
                "ventaItemList.presentacion.id", O, "ventaItemList.presentacion.descripcion", O,
                "ventaItemList.presentacion.codigoPrincipal.codigo", O));
        Set<String> atrVenta = set("formas_pago", "monedas", "total_gs", "caja_id", "es_delivery");
        agregar("venta.concluida", Familia.CAMBIO, atrVenta, ventaConItems);
        agregar("venta.cobro_editado", Familia.CAMBIO, set("formas_pago", "monedas", "caja_id"), venta);

        // ---------------------------------------------------------------- venta_tarjeta
        Map<String, Clase> tarjeta = rutas(
                "id", O, "ventaId", O, "cajaId", O, "sucursalId", O, "estado", O, "origen", O,
                "monto", O, "montoEscaneado", O, "moneda.denominacion", O, "creadoEn", O,
                "terminalPos.id", O, "terminalPos.descripcion", O, "terminalPos.serie", O,
                "terminalPos.adquirente", O,
                "codigoAutorizacion", S, "numeroBoleta", S, "qrCrudo", S,
                "noCompletadoMotivo", O, "noCompletadoEn", O);
        Set<String> atrTarjeta = set("estado", "terminal_id", "adquirente", "caja_id");
        agregar("venta_tarjeta.creada", Familia.CAMBIO, atrTarjeta, tarjeta);
        agregar("venta_tarjeta.completada", Familia.CAMBIO, atrTarjeta, tarjeta);
        agregar("venta_tarjeta.no_completada", Familia.ALERTA, atrTarjeta, tarjeta);

        // ---------------------------------------------------------------- caja
        Map<String, Clase> caja = rutas(
                "caja.id", O, "sucursalId", O, "usuario.id", O, "usuario.nickname", P,
                "maletin.id", O, "maletin.descripcion", O, "fechaApertura", O, "fechaCierre", O,
                "conteo.moneda", O, "conteo.total", O);
        agregar("caja.abierta", Familia.CAMBIO, set("caja_id", "usuario_id"), caja);
        Map<String, Clase> cierre = new LinkedHashMap<>(caja);
        cierre.putAll(rutas("diferencias.moneda", O, "diferencias.esperado", O, "diferencias.contado", O,
                "diferencias.diferencia", O));
        Set<String> atrCierre = set("caja_id", "usuario_id", "con_diferencia", "diferencia_gs", "diferencia_rs", "diferencia_ds");
        agregar("caja.cerrada", Familia.CAMBIO, atrCierre, cierre);
        agregar("caja.cierre_con_diferencia", Familia.ALERTA, atrCierre, cierre);
        agregar("caja.conteo_editado", Familia.ALERTA, set("caja_id", "apertura"), rutas(
                "caja.id", O, "sucursalId", O, "apertura", O, "usuario.id", O, "usuario.nickname", P,
                "conteoAnterior.moneda", O, "conteoAnterior.total", O,
                "conteoNuevo.moneda", O, "conteoNuevo.total", O));
        agregar("caja.reabierta", Familia.ALERTA, set("caja_id"), rutas(
                "caja.id", O, "sucursalId", O, "usuario.id", O, "motivo", P, "reabiertaEn", O));

        // ---------------------------------------------------------------- retiro y gasto
        Map<String, Clase> retiro = rutas(
                "id", O, "caja.id", O, "sucursalId", O, "usuario.id", O, "responsable.nickname", P,
                "creadoEn", O, "detalle.moneda", O, "detalle.valor", O);
        Set<String> atrRetiro = set("caja_id", "monto_gs");
        agregar("retiro.creado", Familia.CAMBIO, atrRetiro, retiro);
        agregar("retiro.editado", Familia.CAMBIO, atrRetiro, retiro);
        agregar("retiro.eliminado", Familia.ALERTA, atrRetiro, retiro);
        Map<String, Clase> gasto = rutas(
                "id", O, "caja.id", O, "sucursalId", O, "tipoGasto.id", O, "tipoGasto.descripcion", O,
                "valorGs", O, "valorRs", O, "valorDs", O, "descripcion", P,
                "autorizadoPor.nickname", P, "usuario.id", O, "creadoEn", O);
        Set<String> atrGasto = set("caja_id", "tipo_gasto_id", "monto_gs");
        agregar("gasto.creado", Familia.CAMBIO, atrGasto, gasto);
        agregar("gasto.editado", Familia.CAMBIO, atrGasto, gasto);
        agregar("gasto.eliminado", Familia.ALERTA, atrGasto, gasto);

        // ---------------------------------------------------------------- items cancelados
        agregar("venta.items_cancelados", Familia.ALERTA, set("caja_id"), rutas(
                "venta.id", O, "caja.id", O, "usuario.id", O,
                "items.id", O, "items.producto.id", O, "items.presentacion.id", O,
                "items.cantidad", O, "items.precio", O, "items.valorTotal", O));
    }

    private CatalogoEventos() {
    }

    private static void agregar(String nombre, Familia f, Set<String> atributos, Map<String, Clase> rutas) {
        TIPOS.put(nombre, new Tipo(nombre, f, atributos, rutas));
    }

    private static Set<String> set(String... s) {
        return new LinkedHashSet<>(Arrays.asList(s));
    }

    private static Map<String, Clase> rutas(Object... pares) {
        Map<String, Clase> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) m.put((String) pares[i], (Clase) pares[i + 1]);
        return m;
    }

    public static Tipo tipo(String nombre) {
        return TIPOS.get(nombre);
    }

    public static boolean tieneProyeccion(String nombre) {
        return TIPOS.containsKey(nombre);
    }

    public static Map<String, Tipo> todos() {
        return Collections.unmodifiableMap(TIPOS);
    }
}
