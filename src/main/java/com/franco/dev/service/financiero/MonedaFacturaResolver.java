package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.service.operaciones.MonedaVenta;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * En que moneda sale la factura legal de una venta (modulo FACTURACION de la moneda por modulo).
 * <p>
 * La moneda es la configurada para FACTURACION en esta sucursal y, sin configuracion, la de la venta.
 * Si resulta la de la empresa (todas las ventas de hoy) la factura sale como siempre: vacio. Si es
 * otra, la factura sigue en la moneda de la empresa con {@code monedaExtranjera} y {@code tipoCambio}
 * (el camino que ya usan SIFEN y el ticket en moneda extranjera). El tipo de cambio es la cotizacion
 * de la venta cuando la factura va en la moneda de la venta; si no, la del comercio de hoy.
 * <p>
 * Nunca lanza: una factura en otra moneda sin cotizacion sale en la moneda de la empresa y lo deja en
 * el log (FACTURA_MONEDA_SIN_COTIZACION).
 */
@Service
@AllArgsConstructor
public class MonedaFacturaResolver {

    private static final Logger log = LoggerFactory.getLogger(MonedaFacturaResolver.class);

    private final MonedaModuloLector lector;
    private final MonedaService monedaService;
    private final CambioService cambioService;

    @Value
    public static class MonedaFactura {
        Moneda moneda;
        String codigoIso;
        Double tipoCambio;
    }

    public Optional<MonedaFactura> para(Venta venta) {
        try {
            Moneda empresa = monedaService.principal();
            Moneda monedaVenta = venta != null ? venta.getMoneda() : null;
            Moneda moneda = lector.configurada(MonedaModuloLector.FACTURACION).orElse(monedaVenta);
            if (!MonedaVenta.esOtraMoneda(moneda, empresa)) return Optional.empty();
            Double tipoCambio = null;
            if (monedaVenta != null && Objects.equals(moneda.getId(), monedaVenta.getId()) && venta.getCotizacion() != null
                    && venta.getCotizacion().signum() > 0) {
                tipoCambio = venta.getCotizacion().doubleValue();
            } else {
                tipoCambio = cambioService.findLastValorEnGsByMonedaIdOrDefault(moneda.getId(), null);
            }
            String iso = CodigoIsoMoneda.de(moneda);
            if (tipoCambio == null || iso == null) {
                log.warn("FACTURA_MONEDA_SIN_COTIZACION venta={} moneda={} iso={}: sale en la moneda de la empresa",
                        venta != null ? venta.getId() : null, moneda.getId(), iso);
                return Optional.empty();
            }
            return Optional.of(new MonedaFactura(moneda, iso, tipoCambio));
        } catch (Exception e) {
            log.warn("FACTURA_MONEDA_ILEGIBLE venta={}: sale en la moneda de la empresa: {}",
                    venta != null ? venta.getId() : null, e.getMessage());
            return Optional.empty();
        }
    }
}
