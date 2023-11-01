package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.graphql.operaciones.input.*;
import com.franco.dev.service.general.BarrioService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.PrecioDeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.operaciones.VueltoService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.GraphQLException;
import graphql.GraphqlErrorException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class DeliveryGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private DeliveryService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private PrecioDeliveryService deliveryPrecioService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private BarrioService barrioService;

    @Autowired
    private VueltoService vueltoService;

    @Autowired
    private VentaItemGraphQL ventaItemGraphQL;

    @Autowired
    private VentaGraphQL ventaGraphQL;

    @Autowired
    private VueltoGraphQL vueltoGraphQL;

    @Autowired
    private VueltoItemGraphQL vueltoItemGraphQL;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private CobroGraphQL cobroGraphQL;

    @Autowired
    private CobroDetalleGraphQL cobroDetalleGraphQL;

    @Autowired
    private FacturaLegalGraphQL facturaLegalGraphQL;

    public Optional<Delivery> delivery(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<Delivery> deliverys(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Delivery> deliverysByEstado(DeliveryEstado estado, Long sucId) {
        return service.findByEstado(estado);
    }

    public List<Delivery> deliverysByEstadoList(List<DeliveryEstado> estadoList, Long sucId) {
        return service.findByEstadoList(estadoList, sucId);
    }

    public List<Delivery> deliverysByEstadoNotIn(DeliveryEstado estado, Long sucId) {
        return service.findByEstadoNotIn(estado);
    }

    public List<Delivery> deliverysUltimos10(Long sucId) {
        return service.findTop10();
    }


    public Delivery saveDelivery(DeliveryInput input) {
        ModelMapper m = new ModelMapper();
        Delivery e = m.map(input, Delivery.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getFuncionarioId() != null) {
            e.setEntregador(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        }
        if (input.getVentaId() != null) {
            e.setVenta(ventaService.findById(input.getVentaId()).orElse(null));
        }
        if (input.getPrecioId() != null) {
            e.setPrecio(deliveryPrecioService.findById(input.getPrecioId()).orElse(null));
        }
        if (input.getBarrioId() != null) {
            e.setBarrio(barrioService.findById(input.getBarrioId()).orElse(null));
        }

        if (input.getVueltoId() != null) {
            e.setVuelto(vueltoService.findById(input.getVueltoId()).orElse(null));
        }
        return service.saveAndSend(e, false);
    }

    @Transactional
    public Delivery saveDeliveryAndVenta(DeliveryInput deliveryInput, VentaInput ventaInput, List<VentaItemInput> ventaItemInputList, VueltoInput vueltoInput, List<VueltoItemInput> vueltoItemInputList, CobroInput cobroInput, List<CobroDetalleInput> cobroDetalleInputList) throws GraphqlErrorException {
        Delivery delivery = null;
        try {
            if (cobroInput == null && cobroDetalleInputList != null) {
                cobroInput = new CobroInput();
                cobroInput.setUsuarioId(deliveryInput.getUsuarioId());
            }

            Cobro cobro = cobroInput != null ? cobroGraphQL.saveCobro(cobroInput, cobroDetalleInputList, ventaInput.getCajaId()) : null;

            if (cobro != null) {
                ventaInput.setCobroId(cobro.getId());
            }

            Venta venta = ventaGraphQL.saveVenta(ventaInput);
            if (venta != null) {
                List<VentaItem> ventaItemList = ventaItemInputList.size() > 0 ? ventaItemGraphQL.saveVentaItemList(ventaItemInputList, venta.getId()) : null;
                deliveryInput.setVentaId(venta.getId());
            }

            delivery = saveDelivery(deliveryInput);
            if (delivery != null) {
                return delivery;
            } else {
                throw new GraphQLException("Algo salió mal. Comuniquese con el soporte");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("Algo salió mal. Comuniquese con el soporte");
        }

    }

    public Boolean deleteDelivery(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Delivery saveDeliveryEstado(Long deliveryId, DeliveryEstado deliveryEstado, String printerName, String local, Long pdvId) throws GraphQLException {
        Delivery delivery = service.findById(deliveryId).orElse(null);
        try {
            delivery.setEstado(deliveryEstado);
            delivery = service.saveAndSend(delivery, false);
            switch (deliveryEstado) {
                case PARA_ENTREGA:
                    List<VentaItem> ventaItemList = ventaItemGraphQL.ventaItemListPorVentaId(delivery.getVenta().getId(), null);
                    if (pdvId != null) {
                        FacturaLegalInput facturaLegalInput = new FacturaLegalInput();
                        if (delivery.getVenta().getCliente() == null) {
                            facturaLegalInput.setNombre("SIN NOMBRE");
                            facturaLegalInput.setRuc("X");
                        } else {
                            facturaLegalInput.setNombre(delivery.getVenta().getCliente().getPersona().getNombre());
                            facturaLegalInput.setRuc(delivery.getVenta().getCliente().getPersona().getDocumento());
                        }
                        facturaLegalInput.setVentaId(delivery.getVenta().getId());
                        facturaLegalInput.setCredito(false);
                        facturaLegalInput.setUsuarioId(delivery.getVenta().getUsuario().getId());
                        List<FacturaLegalItemInput> facturaLegalItemInputList = new ArrayList<>();
                        for (VentaItem vi : ventaItemList) {
                            FacturaLegalItemInput fiInput = new FacturaLegalItemInput();
                            fiInput.setVentaItemId(vi.getId());
                            fiInput.setPresentacionId(vi.getPresentacion().getId());
                            fiInput.setIva(vi.getPresentacion().getProducto().getIva());
                            fiInput.setDescripcion(vi.getPresentacion().getProducto().getDescripcionFactura());
                            fiInput.setCantidad(vi.getCantidad());
                            fiInput.setPrecioUnitario(vi.getPrecioVenta().getPrecio() - vi.getValorDescuento());
                            fiInput.setTotal(fiInput.getCantidad() * fiInput.getPrecioUnitario());
                            facturaLegalItemInputList.add(fiInput);
                        }
                        FacturaLegalItemInput fiInput = new FacturaLegalItemInput();
                        fiInput.setVentaItemId(null);
                        fiInput.setPresentacionId(null);
                        fiInput.setIva(10);
                        fiInput.setDescripcion("Delivery");
                        fiInput.setCantidad(Double.valueOf(1));
                        fiInput.setPrecioUnitario(delivery.getPrecio().getValor());
                        fiInput.setTotal(delivery.getPrecio().getValor());
                        facturaLegalItemInputList.add(fiInput);
//                        SaveFacturaDto saveFacturaDto = facturaService.printTicket58mmFactura(venta, facturaLegalInput, facturaLegalItemInputList, printerName, pdvId, false);
                        Boolean facturado = facturaLegalGraphQL.saveFacturaLegal(facturaLegalInput, facturaLegalItemInputList, printerName, pdvId, true);
                        if (facturado == false) throw new GraphQLException("Problema al generar factura");
                    } else {
                        ventaGraphQL.printTicket58mm(delivery.getVenta(), null, ventaItemList, null, false, printerName, local, false, null, delivery);
                    }
                    ventaGraphQL.printTicket58mm(delivery.getVenta(), null, ventaItemList, null, true, printerName, local, false, null, delivery);
                    break;
                case CONCLUIDO:
                    delivery.setEstado(DeliveryEstado.CONCLUIDO);
                    Venta venta = delivery.getVenta();
                    venta.setEstado(VentaEstado.CONCLUIDA);
                    ventaService.saveAndSend(venta, false);
                    service.saveAndSend(delivery, false);
                    break;
            }
            return delivery;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("Delivery no encontrado");
        }
    }

    public Boolean reimprimirDelivery(Long id, String printerName, String local) {
        Delivery delivery = service.findById(id).orElse(null);
        try {
            List<VentaItem> ventaItemList = ventaItemGraphQL.ventaItemListPorVentaId(delivery.getVenta().getId(), null);
            ventaGraphQL.printTicket58mm(delivery.getVenta(), null, ventaItemList, null, true, printerName, local, false, null, delivery);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("Delivery no encontrado");
        }
    }

    public List<Delivery> deliveryPorCajaIdAndEstados(Long id, List<DeliveryEstado> estadoList){
        return service.findByVentaCajaIdAndEstadoIn(id, estadoList);
    }

    public Long countDelivery() {
        return service.count();
    }
}
