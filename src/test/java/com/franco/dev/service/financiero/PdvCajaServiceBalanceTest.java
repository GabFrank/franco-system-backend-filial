package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.repository.operaciones.DeliveryRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Balance de caja con ventas canceladas (caso real: caja 7440, sucursal 1, 25/10/2026). El vuelto
 * se guarda negativo; restarlo en una cancelada lo sumaba y el cierre del filial daba de faltante
 * el vuelto de cada venta cancelada. El central ya ignoraba las canceladas.
 */
class PdvCajaServiceBalanceTest {

    private static final long CAJA_ID = 7440L;

    @Mock private ConteoMonedaService conteoMonedaService;
    @Mock private MovimientoCajaService movimientoCajaService;
    @Mock private RetiroDetalleService retiroDetalleService;
    @Mock private GastoService gastoService;
    @Mock private VentaService ventaService;
    @Mock private CobroService cobroService;
    @Mock private CobroDetalleService cobroDetalleService;
    @Mock private DeliveryService deliveryService;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private SucursalService sucursalService;
    @InjectMocks private PdvCajaService service;

    private final List<Venta> ventas = new ArrayList<>();
    private final List<MovimientoCaja> movimientos = new ArrayList<>();
    private final Map<Long, List<CobroDetalle>> detalles = new HashMap<>();
    private long proximoId = 1;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(deliveryService.getRepository()).thenReturn(deliveryRepository);
        when(ventaService.findAllByCajaId(CAJA_ID)).thenReturn(ventas);
        when(movimientoCajaService.findByPdvCajaId(CAJA_ID)).thenReturn(movimientos);
        when(cobroService.findById(anyLong())).thenAnswer(inv -> {
            Cobro c = new Cobro();
            c.setId(inv.getArgument(0));
            return Optional.of(c);
        });
        when(cobroDetalleService.findByCobroId(anyLong()))
                .thenAnswer(inv -> detalles.getOrDefault(inv.<Long>getArgument(0), new ArrayList<>()));
    }

    @Test
    void ventaCanceladaNoSumaSuVueltoNiDescuentoNiAumento() {
        movimiento(PdvCajaTipoMovimiento.CAJA_INICIAL, "GUARANI", 100_000);
        // contado: apertura + 30.000 + 10.000 netos, con 9.500 de faltante real
        movimiento(PdvCajaTipoMovimiento.CAJA_FINAL, "GUARANI", 130_500);
        venta(VentaEstado.CONCLUIDA, 30_000, pago("GUARANI", 50_000), vuelto("GUARANI", -20_000));
        venta(VentaEstado.CANCELADA, 32_000, pago("GUARANI", 102_000), vuelto("GUARANI", -70_000),
                descuento(2_000), aumento(1_000));
        venta(VentaEstado.EN_VERIFICACION, 10_000, pago("GUARANI", 10_000));
        venta(VentaEstado.ABIERTA, 5_000);

        PdvCajaBalanceDto b = service.generarBalance(caja());

        igual(-20_000.0, b.getVueltoGs());
        igual(60_000.0, b.getTotalVentaGs());
        igual(-9_500.0, b.getDiferenciaGs());
        igual(102_000.0, b.getTotalCanceladasGs());
        igual(0.0, b.getTotalDescuento());
        igual(0.0, b.getTotalAumento());
        igual(40_000.0, b.getTotalGeneral(), "solo concluidas y en verificacion");
    }

    @Test
    void ventaCanceladaEnRealesYDolaresNoSumaSuVuelto() {
        movimiento(PdvCajaTipoMovimiento.CAJA_INICIAL, "REAL", 100);
        movimiento(PdvCajaTipoMovimiento.CAJA_INICIAL, "DOLAR", 50);
        movimiento(PdvCajaTipoMovimiento.CAJA_FINAL, "REAL", 100);
        movimiento(PdvCajaTipoMovimiento.CAJA_FINAL, "DOLAR", 50);
        venta(VentaEstado.CANCELADA, 40_000, pago("REAL", 20), vuelto("REAL", -5),
                pago("DOLAR", 10), vuelto("DOLAR", -3));

        PdvCajaBalanceDto b = service.generarBalance(caja());

        igual(0.0, b.getVueltoRs());
        igual(0.0, b.getVueltoDs());
        igual(0.0, b.getDiferenciaRs());
        igual(0.0, b.getDiferenciaDs());
        igual(20.0, b.getTotalCanceladasRs());
        igual(10.0, b.getTotalCanceladasDs());
    }

    @Test
    void ventaCanceladaConFlagsNulosNoRompeElBalance() {
        movimiento(PdvCajaTipoMovimiento.CAJA_INICIAL, "GUARANI", 100_000);
        CobroDetalle sinFlags = detalle("GUARANI", 15_000, null, null, null, null);
        venta(VentaEstado.CANCELADA, 15_000, sinFlags);

        PdvCajaBalanceDto b = service.generarBalance(caja());

        igual(0.0, b.getTotalCanceladasGs());
        igual(-100_000.0, b.getDiferenciaGs(), "caja abierta: saldo = apertura");
    }

    private static void igual(double esperado, Double real) {
        igual(esperado, real, null);
    }

    private static void igual(double esperado, Double real, String mensaje) {
        assertEquals(esperado, real, 0.001, mensaje);
    }

    private PdvCaja caja() {
        PdvCaja caja = new PdvCaja();
        caja.setId(CAJA_ID);
        caja.setSucursalId(1L);
        Conteo apertura = new Conteo();
        apertura.setId(1L);
        caja.setConteoApertura(apertura);
        if (movimientos.stream().anyMatch(m -> m.getTipoMovimiento() == PdvCajaTipoMovimiento.CAJA_FINAL)) {
            Conteo cierre = new Conteo();
            cierre.setId(2L);
            caja.setConteoCierre(cierre);
        }
        return caja;
    }

    private void movimiento(PdvCajaTipoMovimiento tipo, String moneda, double cantidad) {
        MovimientoCaja m = new MovimientoCaja();
        m.setTipoMovimiento(tipo);
        m.setMoneda(moneda(moneda));
        m.setCantidad(cantidad);
        movimientos.add(m);
    }

    private void venta(VentaEstado estado, double totalGs, CobroDetalle... cobro) {
        Cobro c = new Cobro();
        c.setId(proximoId++);
        Venta v = new Venta();
        v.setEstado(estado);
        v.setTotalGs(totalGs);
        v.setCobro(c);
        ventas.add(v);
        detalles.put(c.getId(), Arrays.asList(cobro));
    }

    private static CobroDetalle pago(String moneda, double valor) {
        return detalle(moneda, valor, true, false, false, false);
    }

    private static CobroDetalle vuelto(String moneda, double valor) {
        return detalle(moneda, valor, false, true, false, false);
    }

    private static CobroDetalle descuento(double valor) {
        return detalle("GUARANI", valor, false, false, true, false);
    }

    private static CobroDetalle aumento(double valor) {
        return detalle("GUARANI", valor, false, false, false, true);
    }

    private static CobroDetalle detalle(String moneda, double valor, Boolean pago, Boolean vuelto,
                                        Boolean descuento, Boolean aumento) {
        CobroDetalle d = new CobroDetalle();
        d.setMoneda(moneda(moneda));
        FormaPago fp = new FormaPago();
        fp.setDescripcion("EFECTIVO");
        d.setFormaPago(fp);
        d.setValor(valor);
        d.setCambio(1.0);
        d.setPago(pago);
        d.setVuelto(vuelto);
        d.setDescuento(descuento);
        d.setAumento(aumento);
        return d;
    }

    private static Moneda moneda(String denominacion) {
        Moneda m = new Moneda();
        m.setDenominacion(denominacion);
        return m;
    }
}
