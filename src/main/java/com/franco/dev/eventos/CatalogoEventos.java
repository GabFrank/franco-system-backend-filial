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
                "id", O, "estado", O, "creadoEn", O, "sucursalId", O, "sucursal.id", O,
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
                "cobro.cobroDetalleList.moneda.id", O, "cobro.cobroDetalleList.moneda.denominacion", O,
                "cobro.cobroDetalleList.formaPago.id", O, "cobro.cobroDetalleList.formaPago.descripcion", O);
        Map<String, Clase> ventaConItems = new LinkedHashMap<>(venta);
        ventaConItems.putAll(rutas(
                "ventaItemList.id", O, "ventaItemList.cantidad", O, "ventaItemList.precio", O,
                "ventaItemList.valorDescuento", O, "ventaItemList.valorTotal", O,
                "ventaItemList.producto.id", O, "ventaItemList.producto.descripcion", O,
                "ventaItemList.presentacion.id", O, "ventaItemList.presentacion.descripcion", O,
                "ventaItemList.presentacion.codigoPrincipal.codigo", O));
        Set<String> atrVenta = set("formas_pago", "formas_pago_id", "monedas", "total_gs", "caja_id", "es_delivery");
        agregar("venta.concluida", Familia.CAMBIO, atrVenta, ventaConItems);
        // Fase 2b: la forma que pidio el cliente, cabecera minima y las lineas como quedaron.
        agregar("venta.cobro_editado", Familia.CAMBIO, set("formas_pago", "formas_pago_id", "monedas", "caja_id"), rutas(
                "venta.id", O, "venta.estado", O, "venta.creadoEn", O, "venta.sucursalId", O,
                "venta.usuario.id", O, "venta.usuario.nickname", P, "venta.caja.id", O,
                "cobroDetalleList.id", O, "cobroDetalleList.valor", O, "cobroDetalleList.cambio", O,
                "cobroDetalleList.pago", O, "cobroDetalleList.vuelto", O, "cobroDetalleList.descuento", O,
                "cobroDetalleList.aumento", O, "cobroDetalleList.identificadorTransaccion", S,
                "cobroDetalleList.moneda.id", O, "cobroDetalleList.moneda.denominacion", O,
                "cobroDetalleList.formaPago.id", O, "cobroDetalleList.formaPago.descripcion", O));

        // ---------------------------------------------------------------- venta_tarjeta
        Map<String, Clase> tarjeta = rutas(
                "id", O, "ventaId", O, "cajaId", O, "caja.id", O, "sucursalId", O, "estado", O, "origen", O,
                "monto", O, "montoEscaneado", O, "moneda.id", O, "moneda.denominacion", O, "creadoEn", O,
                "usuario.id", O, "usuario.nickname", P,
                "terminalPos.id", O, "terminalPos.descripcion", O, "terminalPos.serie", O, "terminalPos.codigo", O,
                "terminalPos.adquirente", O, "terminalPos.proveedorServicio.id", O,
                "terminalPos.proveedorServicio.persona.nombre", O,
                "codigoAutorizacion", S, "numeroBoleta", S, "qrCrudo", S, "imagenUrl", S,
                "noCompletadoMotivo", O, "noCompletadoEn", O,
                "venta.id", O, "venta.estado", O,
                "venta.cobro.cobroDetalleList.id", O, "venta.cobro.cobroDetalleList.valor", O,
                "venta.cobro.cobroDetalleList.cambio", O, "venta.cobro.cobroDetalleList.pago", O,
                "venta.cobro.cobroDetalleList.vuelto", O, "venta.cobro.cobroDetalleList.descuento", O,
                "venta.cobro.cobroDetalleList.aumento", O,
                "venta.cobro.cobroDetalleList.identificadorTransaccion", S,
                "venta.cobro.cobroDetalleList.moneda.id", O, "venta.cobro.cobroDetalleList.moneda.denominacion", O,
                "venta.cobro.cobroDetalleList.formaPago.id", O, "venta.cobro.cobroDetalleList.formaPago.descripcion", O);
        Set<String> atrTarjeta = set("estado", "terminal_id", "adquirente", "caja_id");
        agregar("venta_tarjeta.creada", Familia.CAMBIO, atrTarjeta, tarjeta);
        agregar("venta_tarjeta.completada", Familia.CAMBIO, atrTarjeta, tarjeta);
        agregar("venta_tarjeta.no_completada", Familia.ALERTA, atrTarjeta, tarjeta);
        agregar("venta_tarjeta.cancelada", Familia.ALERTA, atrTarjeta, tarjeta);
        agregar("venta_tarjeta.reabierta", Familia.CAMBIO, atrTarjeta, tarjeta);
        Map<String, Clase> tarjetaEditada = new LinkedHashMap<>(tarjeta);
        tarjetaEditada.putAll(rutas("cambios.monto.antes", O, "cambios.monto.despues", O,
                "cambios.moneda.antes", O, "cambios.moneda.despues", O,
                "cambios.terminalPos.antes", O, "cambios.terminalPos.despues", O));
        agregar("venta_tarjeta.editada", Familia.ALERTA, atrTarjeta, tarjetaEditada);

        // ---------------------------------------------------------------- caja
        Map<String, Clase> caja = rutas(
                "caja.id", O, "id", O, "sucursalId", O, "sucursal.id", O, "usuario.id", O, "usuario.nickname", P,
                "maletin.id", O, "maletin.descripcion", O, "fechaApertura", O, "fechaCierre", O,
                "conteo.moneda", O, "conteo.total", O);
        // Fase 2b: el balance de apertura, la caja anterior del mismo maletin y el estado consecutivo.
        Map<String, Clase> apertura = new LinkedHashMap<>(caja);
        apertura.putAll(rutas("balance.totalAperGs", O, "balance.totalAperRs", O, "balance.totalAperDs", O,
                "cajaAnteriorId", O, "estadoDiferenciaConsecutiva", O,
                "cajaAnterior.id", O, "cajaAnterior.usuario.id", O, "cajaAnterior.usuario.nickname", P,
                "cajaAnterior.fechaCierre", O, "cajaAnterior.balance.totalCierreGs", O,
                "cajaAnterior.balance.totalCierreRs", O, "cajaAnterior.balance.totalCierreDs", O));
        agregar("caja.abierta", Familia.CAMBIO, set("caja_id", "usuario_id"), apertura);
        Map<String, Clase> cierre = new LinkedHashMap<>(caja);
        cierre.put("cajaAnteriorId", O);
        for (String b : new String[]{"totalAper", "totalCierre", "diferencia", "totalVenta", "totalRetiro", "totalGasto", "vuelto"})
            for (String m : new String[]{"Gs", "Rs", "Ds"}) cierre.put("balance." + b + m, O);
        cierre.putAll(rutas("balance.totalTarjeta", O, "balance.totalTransferencia", O, "balance.totalCredito", O,
                "cotizacion.rs", O, "cotizacion.ds", O));
        Set<String> atrCierre = set("caja_id", "usuario_id", "con_diferencia", "diferencia_gs", "diferencia_rs", "diferencia_ds",
                "diferencia_total_gs");
        agregar("caja.cerrada", Familia.CAMBIO, atrCierre, cierre);
        agregar("caja.cierre_con_diferencia", Familia.ALERTA, atrCierre, cierre);
        agregar("caja.maletin_distinto", Familia.ALERTA, set("caja_id", "usuario_id"), rutas(
                "caja.id", O, "caja.sucursal.id", O, "caja.usuario.id", O, "caja.usuario.nickname", P,
                "caja.fechaApertura", O, "caja.maletin.id", O, "caja.maletin.descripcion", O, "sucursalId", O,
                "cajaAnterior.id", O, "cajaAnterior.usuario.id", O, "cajaAnterior.usuario.nickname", P,
                "cajaAnterior.fechaCierre", O,
                "dejo.gs", O, "dejo.rs", O, "dejo.ds", O, "recibio.gs", O, "recibio.rs", O, "recibio.ds", O,
                "diferencia.gs", O, "diferencia.rs", O, "diferencia.ds", O));
        agregar("caja.conteo_editado", Familia.ALERTA, set("caja_id", "apertura"), rutas(
                "caja.id", O, "caja.sucursal.id", O, "caja.fechaCierre", O, "sucursalId", O, "apertura", O, "tipo", O,
                "usuario.id", O, "usuario.nickname", P,
                "conteoAnterior.moneda", O, "conteoAnterior.total", O,
                "conteoNuevo.moneda", O, "conteoNuevo.total", O,
                "balance.diferenciaGs", O, "balance.diferenciaRs", O, "balance.diferenciaDs", O));
        agregar("caja.reabierta", Familia.ALERTA, set("caja_id"), rutas(
                "caja.id", O, "sucursalId", O, "usuario.id", O, "motivo", P, "reabiertaEn", O));

        // ---------------------------------------------------------------- retiro y gasto
        Map<String, Clase> retiro = rutas(
                "id", O, "caja.id", O, "sucursalId", O, "usuario.id", O, "usuario.nickname", P,
                "creadoEn", O, "estado", O, "observacion", P,
                "retiroDetalleList.cantidad", O, "retiroDetalleList.moneda.id", O, "retiroDetalleList.moneda.denominacion", O,
                "responsable.persona.nombre", P, "cajaSalida.id", O, "cajaSalida.maletin.descripcion", O);
        Set<String> atrRetiro = set("caja_id", "monto_gs");
        agregar("retiro.creado", Familia.CAMBIO, atrRetiro, retiro);
        agregar("retiro.editado", Familia.CAMBIO, atrRetiro, retiro);
        agregar("retiro.eliminado", Familia.ALERTA, atrRetiro, retiro);
        Map<String, Clase> gasto = rutas(
                "id", O, "caja.id", O, "sucursalId", O, "sucursal.id", O, "tipoGasto.id", O, "tipoGasto.descripcion", O,
                "valorGs", O, "valorRs", O, "valorDs", O, "descripcion", P, "observacion", P, "cancelado", O,
                "retiroGs", O, "retiroRs", O, "retiroDs", O, "vueltoGs", O, "vueltoRs", O, "vueltoDs", O,
                "responsable.persona.nombre", P,
                "autorizadoPor.nickname", P, "usuario.id", O, "usuario.nickname", P, "creadoEn", O);
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
