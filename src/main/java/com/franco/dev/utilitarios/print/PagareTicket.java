package com.franco.dev.utilitarios.print;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.Style;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;
import static com.franco.dev.utilitarios.DateUtils.toDate;

/**
 * Los pagares de una venta a credito, uno por cuota. Van al pie del ticket de la venta y, si la
 * venta se facturo, en un papel aparte del ticket-factura (issue #133).
 */
public final class PagareTicket {

    private PagareTicket() {
    }

    /**
     * @param center el estilo centrado del ticket que llama: queda en negrita, como siempre.
     */
    public static void escribir(EscPos escpos, Style center, Venta venta, Sucursal sucursal, String local,
            List<VentaCreditoCuotaInput> cuotas) throws IOException {
        DecimalFormat df = new DecimalFormat("#,###.##");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        for (int x = 0; x < cuotas.size(); x++) {
            escpos.writeLF(center, "PAGARE A LA ORDEN " + (x + 1) + "/" + cuotas.size());
            escpos.feed(1);
            escpos.write("Total Gs: ");
            String valorPagare = df.format(cuotas.get(x).getValor().intValue());
            for (int i = 22; i > valorPagare.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(valorPagare);
            escpos.writeLF("Fecha: " + venta.getCreadoEn().format(formatter));
            escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());
            if (sucursal != null) {
                escpos.writeLF(center, "Suc: " + sucursal.getNombre());
                if (sucursal.getCiudad() != null) {
                    escpos.writeLF(center, sucursal.getCiudad().getDescripcion());
                }
            }
            if (local != null) {
                escpos.writeLF(center, "Local: " + local);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("El dia ");
            sb.append(toDate(cuotas.get(x).getVencimiento()).format(formatter));
            sb.append(" pagare solidariamente al Sr. FRANCO AREVALOS S.A. la suma de G$ ");
            sb.append(valorPagare);
            sb.append(
                    " por el valor recibido a mi/nuestra entera satisfaccion. En caso de retardo o incumplimiento total o parcial a la fecha de su vencimiento quedara constituida la MORA automatica, sin necesidad de interpelacion alguna.");
            escpos.write(sb.toString());
            escpos.feed(4);
            escpos.writeLF("   --------------------------   ");
            escpos.writeLF(center, "FIRMA");
            escpos.writeLF(center, "Deudor: " + venta.getCliente().getPersona().getNombre().toUpperCase());
            escpos.writeLF(center, "RUC: " + venta.getCliente().getPersona().getDocumento()
                    + getDigitoVerificadorString(venta.getCliente().getPersona().getDocumento()));
        }
    }
}
