package com.franco.dev.fmc.service;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.fmc.model.PushNotificationRequest;
import java.text.DecimalFormat;
import org.springframework.stereotype.Service;

@Service
public class NotificationTemplateService {

    public PushNotificationRequest gastoRealizado(Gasto gasto, Sucursal sucursal, DecimalFormat decimalFormat) {
        PushNotificationRequest request = base("Gasto realizado", buildGastoMessage(gasto, sucursal, decimalFormat));
        request.setType("GASTO");
        request.setData("/");
        return request;
    }

    public PushNotificationRequest ventaCreditoRealizada(VentaCredito ventaCredito, Sucursal sucursal, DecimalFormat decimalFormat) {
        StringBuilder builder = new StringBuilder();
        builder.append("Se ha detectado una venta a crédito en la sucursal ")
                .append(sucursal != null ? sucursal.getNombre() : "")
                .append(" por el valor de ")
                .append(decimalFormat.format(ventaCredito.getValorTotal()))
                .append(" Gs.");
        PushNotificationRequest request = base("Venta a crédito realizada", builder.toString());
        request.setType("VENTA_CREDITO");
        request.setData("/mis-finanzas/list-convenio/" + ventaCredito.getId() + "/" + ventaCredito.getSucursalId());
        return request;
    }

    public PushNotificationRequest manual(String titulo, String mensaje, String data, String tipo) {
        PushNotificationRequest request = base(titulo, mensaje);
        request.setData(data != null ? data : "/");
        request.setType(tipo != null ? tipo : "MANUAL");
        return request;
    }

    private PushNotificationRequest base(String title, String message) {
        PushNotificationRequest request = new PushNotificationRequest();
        request.setTitle(title);
        request.setMessage(message);
        request.setData("/");
        request.setType("SISTEMA");
        return request;
    }

    private String buildGastoMessage(Gasto gasto, Sucursal sucursal, DecimalFormat decimalFormat) {
        StringBuilder builder = new StringBuilder();
        builder.append("Se ha detectado un gasto a tu nombre en la sucursal ")
                .append(sucursal != null ? sucursal.getNombre() : "")
                .append(" por el valor de ");
        if (gasto.getRetiroGs() != null && gasto.getRetiroGs() > 0) {
            builder.append(decimalFormat.format(gasto.getRetiroGs())).append(" Gs. ");
        }
        if (gasto.getRetiroRs() != null && gasto.getRetiroRs() > 0) {
            builder.append(decimalFormat.format(gasto.getRetiroRs())).append(" Rs. ");
        }
        if (gasto.getRetiroDs() != null && gasto.getRetiroDs() > 0) {
            builder.append(decimalFormat.format(gasto.getRetiroDs())).append(" Ds. ");
        }
        if (sucursal != null && gasto.getUsuario() != null) {
            builder.append("Si desconoce ésta acción contactar con el cajero ")
                    .append(gasto.getUsuario().getNickname() != null ? gasto.getUsuario().getNickname().toUpperCase() : "")
                    .append(" al número ")
                    .append(sucursal.getNroDelivery());
        }
        return builder.toString();
    }
}
