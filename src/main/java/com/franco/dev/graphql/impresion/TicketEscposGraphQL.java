package com.franco.dev.graphql.impresion;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.graphql.financiero.input.SenaCuponInput;
import com.franco.dev.graphql.operaciones.CobroGraphQL;
import com.franco.dev.graphql.operaciones.VentaGraphQL;
import com.franco.dev.graphql.operaciones.VentaItemGraphQL;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.RetiroDetalleService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.impresion.dto.GastoDto;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.impresion.dto.SenaCuponDto;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Impresion del POS desde el cliente: genera el comprobante en ESC/POS y lo devuelve en base64
 * para que el desktop lo imprima en su impresora local (Electron), sin que el filial tenga que
 * alcanzar esa impresora. Cada tipo reproduce la reimpresion que ya existe, con los mismos
 * renderers: el papel sale igual que por el servidor.
 *
 * <p>Nunca busca impresora (printerName va null): el renderer escribe en memoria. Devuelve null si
 * el renderer no escribio nada, y lanza si lo pedido no existe.
 *
 * <p>Lo que se guarda y se imprime en el mismo paso (saveVenta, saveDeliveryEstado) no pasa por
 * aca: esas operaciones devuelven el ticket en {@code ticketEscpos} cuando se piden con
 * {@code imprimirEnCliente: true}. Ver docs/impresion-pos-desde-cliente.md.
 */
@Slf4j
@Component
public class TicketEscposGraphQL implements GraphQLQueryResolver {

    @Autowired private VentaService ventaService;
    @Autowired private FacturaLegalService facturaLegalService;
    @Autowired private FacturaLegalItemService facturaLegalItemService;
    @Autowired private PdvCajaService pdvCajaService;
    @Autowired private GastoService gastoService;
    @Autowired private RetiroService retiroService;
    @Autowired private RetiroDetalleService retiroDetalleService;
    @Autowired private DeliveryService deliveryService;
    @Autowired private CobroGraphQL cobroGraphQL;
    @Autowired private VentaItemGraphQL ventaItemGraphQL;
    @Autowired private VentaGraphQL ventaGraphQL;
    @Autowired private FacturaLegalGraphQL facturaLegalGraphQL;
    @Autowired private ImpresionService impresionService;

    public String ticketEscpos(TicketEscposTipo tipo, Long id, Boolean reimpresion, String local) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        switch (tipo) {
            case VENTA:
                venta(id, local, out);
                break;
            case FACTURA:
                factura(id, out);
                break;
            case BALANCE:
                PdvCaja caja = pdvCajaService.findById(id).orElseThrow(() -> noExiste("La caja", id));
                impresionService.printBalance(pdvCajaService.generarBalance(caja), null, local, out);
                break;
            case GASTO:
                impresionService.printGasto(gastoDto(id, Boolean.TRUE.equals(reimpresion)), null, local, out);
                break;
            case RETIRO:
                impresionService.printRetiro(retiroDto(id), null, local, Boolean.TRUE.equals(reimpresion), out);
                break;
            case DELIVERY:
                delivery(id, local, out);
                break;
        }
        return base64(out);
    }

    /** La sena de un cobro con tarjeta: el mismo DTO que arma imprimirSenaCupon. */
    public String senaCuponEscpos(SenaCuponInput input, String local) {
        if (input == null || input.getQr() == null) {
            throw new GraphQLException("La sena no tiene QR para imprimir");
        }
        SenaCuponDto dto = new SenaCuponDto();
        dto.setVentaId(input.getVentaId());
        dto.setVentaTarjetaId(input.getVentaTarjetaId());
        dto.setCajaId(input.getCajaId());
        dto.setCajero(input.getCajero());
        dto.setTerminal(input.getTerminal());
        dto.setMonto(input.getMonto());
        dto.setMonedaSimbolo(input.getMonedaSimbolo());
        dto.setDecimales(input.getDecimales());
        dto.setQr(input.getQr());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        impresionService.printSenaCupon(dto, null, local, out);
        return base64(out);
    }

    /** Como reimprimirVenta: la factura si la venta tiene, si no el ticket marcado REIMPRESION. */
    private void venta(Long id, String local, ByteArrayOutputStream out) throws Exception {
        Venta venta = ventaService.findById(id).orElseThrow(() -> noExiste("La venta", id));
        FacturaLegal facturaLegal = facturaLegalService.findByVentaId(venta.getId());
        if (facturaLegal != null) {
            factura(facturaLegal.getId(), out);
            return;
        }
        Cobro cobro = cobroGraphQL.cobro(venta.getCobro().getId(), null).orElse(null);
        if (cobro == null) {
            throw new GraphQLException("La venta " + id + " no tiene cobro");
        }
        List<VentaItem> items = ventaItemGraphQL.ventaItemListPorVentaId(venta.getId(), null);
        List<CobroDetalleInput> cobroDetalleList = new ArrayList<>();
        ventaGraphQL.printTicket58mm(venta, cobro, items, cobroDetalleList, true, null, local, null, null,
                venta.getDelivery(), out);
    }

    /** Como reimprimirFacturaLegal. */
    private void factura(Long id, ByteArrayOutputStream out) throws Exception {
        FacturaLegal facturaLegal = facturaLegalService.findById(id).orElseThrow(() -> noExiste("La factura", id));
        List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(id);
        boolean esMonedaExtranjera = facturaLegal.getMonedaExtranjera() != null
                && !facturaLegal.getMonedaExtranjera().trim().isEmpty()
                && facturaLegal.getTipoCambio() != null;
        if (esMonedaExtranjera) {
            facturaLegalGraphQL.printTicket58mmFacturaMonedaExtranjera(facturaLegal.getVenta(), facturaLegal, items,
                    null, facturaLegal.getMonedaExtranjera(), facturaLegal.getTipoCambio(), out);
        } else {
            facturaLegalGraphQL.printTicket58mmFactura(facturaLegal.getVenta(), facturaLegal, items, null, out);
        }
    }

    /** Como reimprimirDelivery. */
    private void delivery(Long id, String local, ByteArrayOutputStream out) throws Exception {
        Delivery delivery = deliveryService.findById(id).orElseThrow(() -> noExiste("El delivery", id));
        Venta venta = ventaService.getRepository().findByDeliveryIdAndSucursalId(delivery.getId(),
                delivery.getSucursalId());
        if (venta == null) {
            throw new GraphQLException("El delivery " + id + " no tiene venta");
        }
        List<VentaItem> items = ventaItemGraphQL.ventaItemListPorVentaId(venta.getId(), null);
        ventaGraphQL.printTicket58mm(venta, null, items, null, true, null, local, false, null, delivery, out);
    }

    /** Como reimprimirGasto, pero la marca de REIMPRESION la decide el cliente. */
    private GastoDto gastoDto(Long id, boolean reimpresion) {
        Gasto gasto = gastoService.findById(id).orElseThrow(() -> noExiste("El gasto", id));
        GastoDto dto = new GastoDto();
        dto.setId(gasto.getId());
        dto.setFecha(gasto.getCreadoEn());
        dto.setUsuario(gasto.getUsuario());
        dto.setResponsable(gasto.getResponsable());
        dto.setAutorizadoPor(gasto.getAutorizadoPor());
        dto.setTipoGasto(gasto.getTipoGasto());
        dto.setObservacion(gasto.getObservacion());
        dto.setRetiroGs(gasto.getRetiroGs());
        dto.setRetiroRs(gasto.getRetiroRs());
        dto.setRetiroDs(gasto.getRetiroDs());
        dto.setVueltoGs(gasto.getVueltoGs());
        dto.setVueltoRs(gasto.getVueltoRs());
        dto.setVueltoDs(gasto.getVueltoDs());
        dto.setCajaId(gasto.getCaja().getId());
        dto.setReimpresion(reimpresion);
        return dto;
    }

    /** Como reimprimirRetiro: los montos salen de los detalles guardados. */
    private RetiroDto retiroDto(Long id) {
        Retiro retiro = retiroService.findById(id).orElseThrow(() -> noExiste("El retiro", id));
        RetiroDto dto = new RetiroDto();
        dto.setId(retiro.getId());
        dto.setCajaId(retiro.getCajaSalida().getId());
        dto.setFecha(retiro.getCreadoEn());
        dto.setResponsable(retiro.getResponsable());
        dto.setRetiroGs(retiroDetalleService.findByRetiroIdAndMonedaId(retiro.getId(), Long.valueOf(1)));
        dto.setRetiroRs(retiroDetalleService.findByRetiroIdAndMonedaId(retiro.getId(), Long.valueOf(2)));
        dto.setRetiroDs(retiroDetalleService.findByRetiroIdAndMonedaId(retiro.getId(), Long.valueOf(3)));
        dto.setUsuario(retiro.getUsuario());
        return dto;
    }

    private static GraphQLException noExiste(String que, Long id) {
        return new GraphQLException(que + " " + id + " no existe en este servidor");
    }

    private static String base64(ByteArrayOutputStream out) {
        return out.size() > 0 ? Base64.getEncoder().encodeToString(out.toByteArray()) : null;
    }
}
