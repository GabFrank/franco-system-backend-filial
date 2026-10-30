package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.graphql.operaciones.input.*;
import com.franco.dev.service.general.BarrioService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.PrecioDeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.operaciones.VueltoService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.financiero.ConfiguracionFacturacionLector;
import com.franco.dev.service.financiero.FacturaService;
import com.franco.dev.service.financiero.PoliticaFacturacion;
import com.franco.dev.service.financiero.PoliticaFacturacionService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.BloqueoTransaccionalService;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.service.operaciones.LoteFefoService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import com.franco.dev.service.seguridad.RolSesionService;
import graphql.GraphQLException;
import graphql.GraphqlErrorException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Component
public class DeliveryGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private AuditorUsuarioId auditorUsuarioId;

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
    private CobroGraphQL cobroGraphQL;

    @Autowired
    private CobroDetalleGraphQL cobroDetalleGraphQL;

    @Autowired
    private FacturaLegalGraphQL facturaLegalGraphQL;

    @Autowired
    private ConfiguracionFacturacionLector configuracionFacturacionLector;

    @Autowired
    private PoliticaFacturacionService politicaFacturacionService;

    @Autowired
    private FacturaService facturaService;

    @Autowired
    private FacturaLegalService facturaLegalService;

    @Autowired
    private BloqueoTransaccionalService bloqueo;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Prefijo del lock que serializa la factura de una venta (#210). */
    static final String CLAVE_FACTURA_VENTA = "factura-venta:";

    /** Por debajo de los 60 s con que el desktop corta la mutation. */
    private static final String ESPERA_FACTURA = "15s";

    static final String MENSAJE_FACTURA_EN_CURSO =
            "La factura de este delivery se está generando en otra operación. Reintentá en unos segundos.";

    @Autowired
    private RolSesionService rolSesionService;

    /** Un delivery lo opera el cajero de la caja de su venta, o ANALISIS DE CAJA. Sin venta todavia, pasa. */
    public Optional<Delivery> delivery(Long id, Long sucId) {
        Optional<Delivery> delivery = service.findById(id);
        delivery.map(this::ventaDe).ifPresent(v -> pdvCajaService.exigirAccesoARegistroDeCaja(v.getCaja()));
        return delivery;
    }

    // Las listas de toda la sucursal no las usa ningun cliente (el PDV lista por caja): solo ANALISIS DE CAJA.
    public List<Delivery> deliverys(int page, int size, Long sucId) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Delivery> deliverysByEstado(DeliveryEstado estado, Long sucId) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        return service.findByEstado(estado);
    }

    public List<Delivery> deliverysByEstadoList(List<DeliveryEstado> estadoList, Long sucId) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        return service.getRepository().findDeliveryByEstadoAndSucId(estadoList, sucId);
    }

    public List<Delivery> deliverysByEstadoNotIn(DeliveryEstado estado, Long sucId) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        return service.findByEstadoNotIn(estado);
    }

    public List<Delivery> deliverysUltimos10(Long sucId) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        return service.findTop10();
    }

    /** Sin llamadores en los clientes; el PDV guarda con saveDeliveryAndVenta. */
    public Delivery saveDelivery(DeliveryInput input) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        return guardarDelivery(input);
    }

    private Delivery guardarDelivery(DeliveryInput input) {
        auditorUsuarioId.verificar("DeliveryGraphQL.saveDelivery", input.getUsuarioId());
        ModelMapper m = new ModelMapper();
        Delivery e = m.map(input, Delivery.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getFuncionarioId() != null) {
            e.setEntregador(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
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
        if (input.getFechaConcluido() != null) {
            e.setFechaConcluido(stringToDate(input.getFechaConcluido()));
        }
        return service.saveAndSend(e, false);
    }

    @Transactional
    public Delivery saveDeliveryAndVenta(DeliveryInput deliveryInput, VentaInput ventaInput,
            List<VentaItemInput> ventaItemInputList, VueltoInput vueltoInput, List<VueltoItemInput> vueltoItemInputList,
            CobroInput cobroInput, List<CobroDetalleInput> cobroDetalleInputList) throws GraphqlErrorException {
        auditorUsuarioId.verificar("DeliveryGraphQL.saveDeliveryAndVenta", deliveryInput.getUsuarioId());
        exigirAccesoAlGuardar(deliveryInput, ventaInput, cobroInput);
        Delivery delivery = null;
        try {
            if (cobroInput == null && cobroDetalleInputList != null) {
                cobroInput = new CobroInput();
                cobroInput.setUsuarioId(deliveryInput.getUsuarioId());
            }

            Cobro cobro = cobroInput != null
                    ? cobroGraphQL.saveCobro(cobroInput, cobroDetalleInputList, ventaInput.getCajaId())
                    : null;

            if (cobro != null) {
                ventaInput.setCobroId(cobro.getId());
            }

            delivery = guardarDelivery(deliveryInput);
            if (delivery != null) {
                ventaInput.setDeliveryId(delivery.getId());
            }
            Venta venta = null;
            boolean isVentaNueva = ventaInput.getId() == null;
            if (!isVentaNueva) {
                venta = ventaService.findById(ventaInput.getId()).orElse(null);
                if (venta != null) {
                    ventaInput.setEstado(venta.getEstado());
                    ventaInput.setCajaId(venta.getCaja().getId());
                    ventaInput.setTotalGs(venta.getTotalGs());
                    ventaInput.setTotalRs(venta.getTotalRs());
                    ventaInput.setTotalDs(venta.getTotalDs());
                    ventaInput.setDeliveryId(venta.getDelivery().getId());
                }
            }
            venta = ventaGraphQL.saveVenta2(ventaInput);
            if (venta != null) {
                // Solo guardar items si la venta es nueva
                // Esto evita re-guardar items existentes que causan duplicación de movimientos
                // de stock
                if (isVentaNueva && ventaItemInputList != null && ventaItemInputList.size() > 0) {
                    ventaItemGraphQL.saveVentaItemList(ventaItemInputList, venta.getId());
                }
                // Si la venta ya existe, no re-guardamos los items para evitar duplicados
                // Si en el futuro se necesita actualizar items existentes, se debe hacer de
                // forma explícita
                // y verificando cambios antes de guardar
            }

            if (delivery != null) {
                return delivery;
            } else {
                throw new GraphQLException("Algo salió mal. Comuniquese con el soporte");
            }
        } catch (GraphQLException e) {
            // Ya trae un mensaje para el cajero, como el de reintentar del lock de saldo por lote (#185).
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("Algo salió mal. Comuniquese con el soporte");
        }

    }

    /**
     * Antes de cualquier escritura: los ids del input se cruzan contra lo guardado (la venta tiene que ser la del
     * delivery y el cobro el de la venta) y se exige la caja. Al editar manda la caja de la venta guardada, tambien
     * para el movimiento que hace saveCobro; al crear, la caja que llega tiene que existir.
     */
    private void exigirAccesoAlGuardar(DeliveryInput deliveryInput, VentaInput ventaInput, CobroInput cobroInput) {
        Delivery guardado = null;
        if (deliveryInput.getId() != null) {
            guardado = service.findById(deliveryInput.getId()).orElse(null);
            if (guardado == null) {
                throw new GraphQLException("Delivery no encontrado");
            }
        }
        Venta ventaGuardada = guardado != null ? ventaDe(guardado) : null;
        Long ventaId = ventaInput != null ? ventaInput.getId() : null;
        boolean mismaVenta = ventaGuardada == null ? ventaId == null : ventaGuardada.getId().equals(ventaId);
        if (!mismaVenta) {
            throw new GraphQLException("La venta no corresponde a este delivery");
        }
        Long cobroId = cobroInput != null ? cobroInput.getId() : null;
        if (cobroId != null && (ventaGuardada == null || ventaGuardada.getCobro() == null
                || !cobroId.equals(ventaGuardada.getCobro().getId()))) {
            throw new GraphQLException("El cobro no corresponde a esta venta");
        }
        // Sin cobroInput, el cobroId del cliente quedaria en la venta: sale de la venta guardada (o se crea despues).
        if (ventaInput != null) {
            ventaInput.setCobroId(ventaGuardada != null && ventaGuardada.getCobro() != null
                    ? ventaGuardada.getCobro().getId() : null);
        }
        if (ventaGuardada != null) {
            pdvCajaService.exigirAccesoARegistroDeCaja(ventaGuardada.getCaja());
            if (ventaGuardada.getCaja() != null) {
                ventaInput.setCajaId(ventaGuardada.getCaja().getId());
            }
        } else {
            Long cajaId = ventaInput != null ? ventaInput.getCajaId() : null;
            if (cajaId == null || !pdvCajaService.findById(cajaId).isPresent()) {
                throw new GraphQLException("Caja no encontrada");
            }
            pdvCajaService.exigirAccesoACaja(cajaId);
        }
    }

    private Venta ventaDe(Delivery delivery) {
        return ventaService.getRepository().findByDeliveryIdAndSucursalId(delivery.getId(), delivery.getSucursalId());
    }

    public Boolean deleteDelivery(Long id, Long sucId) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        return service.deleteById(id);
    }

    public Delivery saveDeliveryEstado(Long deliveryId, DeliveryEstado deliveryEstado, String printerName, String local,
            Long pdvId, Boolean imprimirEnCliente) throws GraphQLException {
        // Impresion desde el cliente: los mismos comprobantes, escritos en memoria en el mismo orden,
        // vuelven en delivery.ticketEscpos en vez de salir por la impresora del filial.
        ByteArrayOutputStream ticketCliente = Boolean.TRUE.equals(imprimirEnCliente)
                ? new ByteArrayOutputStream() : null;
        Delivery delivery = service.findById(deliveryId).orElse(null);
        Venta venta = delivery != null ? ventaDe(delivery) : null;
        if (venta == null) {
            throw new GraphQLException("Delivery no encontrado");
        }
        pdvCajaService.exigirAccesoARegistroDeCaja(venta.getCaja());
        try {
            delivery.setEstado(deliveryEstado);
            delivery = service.saveAndSend(delivery, false);
            switch (deliveryEstado) {
                case PARA_ENTREGA:
                    List<VentaItem> ventaItemList = ventaItemGraphQL.ventaItemListPorVentaId(venta.getId(), null);
                    // Misma regla que "Venta + Ticket": factura siempre, salvo que la politica de la
                    // sucursal diga que se respete (issue #127). Si no factura, sale el ticket simple.
                    // Un reintento despues de que fallo la impresion reimprime la factura que ya se creo,
                    // sin otra factura ni otro turno de la politica (issue #207).
                    // Afuera de la transaccion: resolver() es NOT_SUPPORTED y adentro pediria otra conexion.
                    PoliticaFacturacion politica = configuracionFacturacionLector.resolver();
                    FacturaLegal factura = facturaDeEntrega(venta, ventaItemList, delivery.getSucursalId(), pdvId,
                            politica);
                    if (factura != null) {
                        // Despues del commit: una falla de la impresora no es una falla de la factura, que ya existe.
                        facturaLegalGraphQL.printTicket58mmFactura(venta, factura, null, printerName, ticketCliente);
                    } else {
                        ventaGraphQL.printTicket58mm(venta, null, ventaItemList, null, false, printerName, local, false,
                                null, delivery, ticketCliente);
                    }
                    ventaGraphQL.printTicket58mm(venta, null, ventaItemList, null, true, printerName, local, false,
                            null, delivery, ticketCliente);
                    if (ticketCliente != null && ticketCliente.size() > 0) {
                        delivery.setTicketEscpos(Base64.getEncoder().encodeToString(ticketCliente.toByteArray()));
                    }
                    break;
                case CONCLUIDO:
                    delivery.setEstado(DeliveryEstado.CONCLUIDO);
                    venta.setEstado(VentaEstado.CONCLUIDA);
                    ventaService.saveAndSend(venta, false);
                    service.saveAndSend(delivery, false);
                    break;
            }
            return delivery;
        } catch (GraphQLException e) {
            throw e;
        } catch (IOException e) {
            // La impresora fallo: el delivery ya quedo PARA_ENTREGA y su factura, si tiene, creada.
            e.printStackTrace();
            String motivo = String.valueOf(e.getMessage());
            throw new GraphQLException(motivo + (motivo.endsWith(".") ? " " : ". ")
                    + "Volve a marcarlo PARA ENTREGA para reimprimir.");
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("Algo salió mal. Comuniquese con el soporte");
        }
    }

    /**
     * La factura vigente de la venta o, si la politica lo pide, una nueva; null si sale el ticket simple. Buscar y
     * crear van en una transaccion con un lock por venta: dos PARA ENTREGA juntos (el reintento de la misma caja
     * despues del corte de 60 s del desktop) creaban dos facturas (#210). El segundo espera al commit del primero
     * y reimprime su factura.
     */
    private FacturaLegal facturaDeEntrega(Venta venta, List<VentaItem> items, Long sucursalId, Long pdvId,
            PoliticaFacturacion politica) {
        boolean[] tomoTurno = {false};
        try {
            // Envuelve tambien el commit: una falla del DE deja la transaccion rollback-only y sale recien ahi.
            return new TransactionTemplate(transactionManager).execute(status -> {
                bloqueo.tomar(CLAVE_FACTURA_VENTA + sucursalId + ":" + venta.getId(), ESPERA_FACTURA);
                FacturaLegal existente = facturaLegalService.findVigenteDeVenta(venta.getId(), sucursalId);
                if (existente != null) {
                    return existente;
                }
                if (!politicaFacturacionService.facturarDelivery(pdvId, politica)) {
                    return null;
                }
                tomoTurno[0] = true;
                // Sin input del PDV (null): el descuento lo lee del cobro guardado del delivery
                FacturaLegal nueva = facturaService.crearFacturaLegalDesdeVenta(venta, items, pdvId, null);
                if (nueva == null) {
                    throw new GraphQLException("Problema al crear factura legal");
                }
                return nueva;
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
            if (!tomoTurno[0]) {
                if (LoteFefoService.esEsperaVencida(e)) {
                    throw new GraphQLException(MENSAJE_FACTURA_EN_CURSO);
                }
                throw e;
            }
            if (PoliticaFacturacionService.salioDeUnTurno(PoliticaFacturacionService.RutaVenta.FACTURA_E_IMPRESION,
                    politica) && PoliticaFacturacionService.fallaAntesDeEscribir(e)) {
                politicaFacturacionService.devolverTurno();
            }
            throw new GraphQLException("Problema al generar factura electrónica"
                    + (e instanceof GraphQLException ? ": " + e.getMessage() : ""));
        }
    }

    public Boolean reimprimirDelivery(Long id, String printerName, String local) {
        Delivery delivery = service.findById(id).orElse(null);
        Venta venta = delivery == null ? null
                : ventaService.getRepository().findByDeliveryIdAndSucursalId(delivery.getId(), delivery.getSucursalId());
        if (venta == null) {
            throw new GraphQLException("Delivery no encontrado");
        }
        pdvCajaService.exigirAccesoARegistroDeCaja(venta.getCaja());
        try {
            List<VentaItem> ventaItemList = ventaItemGraphQL.ventaItemListPorVentaId(venta.getId(), null);
            return Boolean.TRUE.equals(ventaGraphQL.printTicket58mm(venta, null, ventaItemList, null, true,
                    printerName, local, false, null, delivery, null, true));
        } catch (Exception e) {
            e.printStackTrace();
            // El motivo solo si es de la impresora (textos propios); el resto queda en el log.
            throw new GraphQLException("No se pudo reimprimir el delivery"
                    + (e instanceof IOException && e.getMessage() != null ? ": " + e.getMessage() : ""));
        }
    }

    @Autowired
    private com.franco.dev.service.financiero.PdvCajaService pdvCajaService;

    public List<Delivery> deliveryPorCajaIdAndEstados(Long id, List<DeliveryEstado> estadoList, Long sucId) {
        pdvCajaService.exigirAccesoACaja(id);
        if (sucId == null)
            sucId = service.env.getProperty("sucursalId", Long.class);
        List<Delivery> deliveryList = service.getRepository().findDeliveryByCajaEstadoAndSucId(id, estadoList, sucId);
        return deliveryList;
    }

    public Long countDelivery() {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        return service.count();
    }
}
