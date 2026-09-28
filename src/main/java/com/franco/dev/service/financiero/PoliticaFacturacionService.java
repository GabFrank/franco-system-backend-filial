package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import org.springframework.stereotype.Service;

/**
 * Decide que comprobante lleva una venta, segun la politica de facturacion (issue #127).
 * <p>
 * Reemplaza al campo {@code facturaCountDown} que vivia en {@code VentaGraphQL}: el contador
 * sigue en memoria (se reinicia con el servicio, como siempre), pero ahora sincronizado. Supuesto:
 * una sola JVM por filial.
 * <p>
 * Con la politica por defecto ({@link PoliticaFacturacion#desdeProperty}) las decisiones son las
 * mismas que antes, con una excepcion declarada: {@code ticket=true, facturar=null} con punto de
 * venta ya no revienta por unboxing, sale ticket simple. {@code PoliticaFacturacionServiceTest}
 * compara contra la logica vieja combinacion por combinacion.
 */
@Service
public class PoliticaFacturacionService {

    /** Que hace {@code saveVenta} con la venta ya guardada. */
    public enum RutaVenta {
        /** Factura legal con DE e impresion del ticket-factura. */
        FACTURA_E_IMPRESION,
        /** Venta a credito: guarda VentaCredito e imprime el pagare. */
        PAGARE_CREDITO,
        /** Ticket simple, sin comprobante fiscal. */
        TICKET_SIMPLE,
        /** Factura legal con DE, sin imprimir. */
        FACTURA_SILENCIOSA,
        /** Nada mas: ni factura ni impresion. */
        SIN_FACTURA
    }

    /** Ventas que faltan para la proxima factura en modo INTERVALO. NULL hasta la primera venta. */
    private Integer contador;

    /**
     * La decision completa de {@code saveVenta}.
     *
     * @param ticket   el cajero pidio impresion ("Venta + Ticket", o venta a credito)
     * @param facturar {@code false} = el frontend ya emitio una factura manual
     * @param pdvId    punto de venta; sin el no se puede facturar
     * @param credito  la venta trae {@code VentaCredito} y sus cuotas
     */
    public synchronized RutaVenta decidirRuta(Boolean ticket, Boolean facturar, Long pdvId, boolean credito,
                                              PoliticaFacturacion politica) {
        if (Boolean.TRUE.equals(ticket)) {
            // Las ventas a credito siguen la ruta de siempre, fuera de "respeta": si no, guardar o no
            // el VentaCredito dependeria del turno. Ver issue #133.
            if (pdvId != null && Boolean.TRUE.equals(facturar)
                    && (credito || facturaInmediata(politica, pdvId))) {
                return RutaVenta.FACTURA_E_IMPRESION;
            }
            return credito ? RutaVenta.PAGARE_CREDITO : RutaVenta.TICKET_SIMPLE;
        }
        if (Boolean.FALSE.equals(facturar)) {
            // Factura manual ya emitida: no se toca el contador de las demas ventas.
            return RutaVenta.SIN_FACTURA;
        }
        return tocaPorPolitica(politica, pdvId) ? RutaVenta.FACTURA_SILENCIOSA : RutaVenta.SIN_FACTURA;
    }

    /** Delivery al pasar a PARA_ENTREGA: misma regla que "Venta + Ticket". */
    public synchronized boolean facturarDelivery(Long pdvId, PoliticaFacturacion politica) {
        return pdvId != null && facturaInmediata(politica, pdvId);
    }

    /**
     * La facturacion del turno fallo antes de escribir nada (validacion, {@code GraphQLException}):
     * la proxima venta vuelve a intentar. Ante cualquier otra falla NO se llama — una falla
     * sistematica (timbrado vencido, SIFEN caido) no puede reintentarse en cada venta.
     */
    public synchronized void devolverTurno() {
        contador = 0;
    }

    /**
     * Si la factura de esta ruta la dio un turno del contador. "Venta + Ticket" que no respeta la
     * politica, o una venta a credito, facturan sin tocar el contador: devolverles un turno que no
     * tomaron adelantaria la factura de otra venta.
     */
    public static boolean salioDeUnTurno(RutaVenta ruta, boolean credito, PoliticaFacturacion politica) {
        if (!ConfiguracionFacturacion.MODO_INTERVALO.equals(politica.getModo())) {
            return false;
        }
        if (ruta == RutaVenta.FACTURA_SILENCIOSA) {
            return true;
        }
        return ruta == RutaVenta.FACTURA_E_IMPRESION && !credito && politica.isVentaTicketRespetaPolitica();
    }

    /**
     * Si la facturacion fallo en una validacion previa a cualquier escritura. {@code saveFacturaLegal}
     * envuelve TODA falla en una {@code GraphQLException}, asi que lo que decide es la causa raiz:
     * una {@code GraphQLException} ahi es de las validaciones de {@code FacturaLegalBuilder}, que
     * corren antes del primer insert. Cualquier otra causa es una falla posterior.
     */
    public static boolean fallaAntesDeEscribir(Throwable error) {
        Throwable raiz = error;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return raiz instanceof graphql.GraphQLException;
    }

    /** "Venta + Ticket" y delivery: facturan siempre, salvo que la politica diga que la respeten. */
    private boolean facturaInmediata(PoliticaFacturacion politica, Long pdvId) {
        return !politica.isVentaTicketRespetaPolitica() || tocaPorPolitica(politica, pdvId);
    }

    private boolean tocaPorPolitica(PoliticaFacturacion politica, Long pdvId) {
        switch (politica.getModo()) {
            case ConfiguracionFacturacion.MODO_TODAS:
                return pdvId != null;
            case ConfiguracionFacturacion.MODO_A_PEDIDO:
                return false;
            default:
                return tomarTurno(politica.getVentasSinFactura(), pdvId);
        }
    }

    /**
     * La cadencia exacta del facturaCountDown historico: con el contador en 0 factura (si hay punto
     * de venta) y lo reinicia a {@code n}; si no, lo decrementa — aunque no haya punto de venta.
     */
    private boolean tomarTurno(int n, Long pdvId) {
        if (contador == null || contador > n) {
            contador = n;
        }
        if (contador == 0) {
            if (pdvId == null) {
                return false;
            }
            contador = n;
            return true;
        }
        contador = contador - 1;
        return false;
    }

    /** Solo para tests. */
    synchronized Integer contadorActual() {
        return contador;
    }

    /** Solo para tests. */
    synchronized void fijarContador(Integer valor) {
        contador = valor;
    }
}
