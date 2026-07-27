package com.franco.dev.service.impresion;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.CobroDetalle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrupa los pagos de un cobro por forma de pago para imprimirlos en el ticket.
 * <p>
 * Una venta puede tener varios CobroDetalle con la misma forma de pago y la misma
 * moneda (ej. dos pagos en efectivo de 50.000 Gs). En el ticket se muestran como
 * una sola linea, por lo que los valores se <b>suman</b>: agrupar quedandose con
 * un solo detalle haria que el ticket imprima menos de lo cobrado.
 * <p>
 * Recibe los detalles de una unica moneda (ya filtrados por el llamador) y respeta
 * el orden de aparicion de cada forma de pago.
 */
public class PagosTicketAgrupador {

    private PagosTicketAgrupador() {
    }

    /**
     * @param detallesMoneda detalles de cobro de una misma moneda; los que no son pago se ignoran
     * @return un PagoAgrupado por forma de pago, con los valores sumados
     */
    public static List<PagoAgrupado> agrupar(List<CobroDetalle> detallesMoneda) {
        Map<Long, PagoAgrupado> porFormaPago = new LinkedHashMap<>();
        if (detallesMoneda == null) {
            return new ArrayList<>();
        }
        for (CobroDetalle cd : detallesMoneda) {
            if (cd == null || cd.getPago() == null || !cd.getPago()) {
                continue;
            }
            Long formaPagoId = cd.getFormaPago() != null ? cd.getFormaPago().getId() : 0L;
            Double valor = cd.getValor() != null ? cd.getValor() : 0.0;
            PagoAgrupado acumulado = porFormaPago.get(formaPagoId);
            if (acumulado == null) {
                porFormaPago.put(formaPagoId, new PagoAgrupado(cd.getMoneda(), cd.getFormaPago(), valor));
            } else {
                porFormaPago.put(formaPagoId, acumulado.sumar(valor));
            }
        }
        return new ArrayList<>(porFormaPago.values());
    }

    /**
     * Una linea de pago del ticket: forma de pago, moneda y el total cobrado con esa forma.
     */
    public static class PagoAgrupado {

        private final Moneda moneda;
        private final FormaPago formaPago;
        private final Double valor;

        public PagoAgrupado(Moneda moneda, FormaPago formaPago, Double valor) {
            this.moneda = moneda;
            this.formaPago = formaPago;
            this.valor = valor;
        }

        private PagoAgrupado sumar(Double otroValor) {
            return new PagoAgrupado(moneda, formaPago, valor + otroValor);
        }

        public Moneda getMoneda() {
            return moneda;
        }

        public FormaPago getFormaPago() {
            return formaPago;
        }

        public Double getValor() {
            return valor;
        }
    }
}
