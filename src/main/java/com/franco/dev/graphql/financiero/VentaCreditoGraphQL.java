package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.MovimientoPersonas;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.financiero.enums.TipoMovimientoPersonas;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput;
import com.franco.dev.graphql.financiero.input.VentaCreditoInput;
import com.franco.dev.graphql.operaciones.VentaGraphQL;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MovimientoPersonasService;
import com.franco.dev.service.financiero.VentaCreditoCuotaService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.franco.dev.service.utils.PrintingService.resize;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;
import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class VentaCreditoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VentaCreditoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private VentaCreditoCuotasGraphQL ventaCreditoCuotasGraphQL;

    @Autowired
    private MovimientoPersonasService movimientoPersonasService;

    @Autowired
    private VentaGraphQL ventaGraphQL;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private VentaItemService ventaItemService;

    @Autowired
    private VentaCreditoCuotaService ventaCreditoCuotaService;

    @Autowired
    private DeliveryService deliveryService;
    
    @Autowired
    private org.springframework.web.client.RestTemplate restTemplate;
    
    @Autowired
    private org.springframework.core.env.Environment env;

    public Optional<VentaCredito> ventaCredito(Long id) {
        return service.findById(id);
    }

    public List<VentaCredito> ventaCreditos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Boolean cobrarVentaCredito(List<VentaCreditoInput> ventaCreditoInputList, List<CobroDetalleInput> cobroList) {
        Double total = 0.0;
        if(ventaCreditoInputList!=null && ventaCreditoInputList.size() > 0) { //cobrar todo
           for(VentaCreditoInput ventaCreditoInput: ventaCreditoInputList){
               total += ventaCreditoInput.getSaldoTotal() != null ? ventaCreditoInput.getSaldoTotal() : ventaCreditoInput.getValorTotal();
               ventaCreditoInput.setEstado(EstadoVentaCredito.FINALIZADO);

           }
        }
        return null;
    }

    public VentaCredito saveVentaCredito(VentaCreditoInput input){
        ModelMapper m = new ModelMapper();
        VentaCredito e = m.map(input, VentaCredito.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getClienteId() != null) e.setCliente(clienteService.findById(input.getClienteId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursalId(input.getSucursalId());
        if (input.getFechaCobro() != null) e.setFechaCobro(toDate(input.getFechaCobro()));
        e = service.saveAndSend(e, false);
        enviarNotificacionVentaCredito(e);
        
        return e;
    }

    @Transactional
    public VentaCredito saveVentaCredito(VentaCreditoInput input, List<VentaCreditoCuotaInput> ventaCreditoCuotaInputList) {
        ModelMapper m = new ModelMapper();
        VentaCredito e = m.map(input, VentaCredito.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getClienteId() != null) e.setCliente(clienteService.findById(input.getClienteId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursalId(input.getSucursalId());
        e = service.saveAndSend(e, false);
        if (e.getId() != null) {
            for (VentaCreditoCuotaInput vc : ventaCreditoCuotaInputList) {
                vc.setVentaCreditoId(e.getId());
                vc.setUsuarioId(input.getUsuarioId());
                VentaCreditoCuota ventaCreditoCuota = ventaCreditoCuotasGraphQL.saveVentaCreditoCuota(vc);
                if (ventaCreditoCuota != null) {
                    MovimientoPersonas movimientoPersonas = new MovimientoPersonas();
                    movimientoPersonas.setVencimiento(ventaCreditoCuota.getVencimiento());
                    movimientoPersonas.setPersona(e.getCliente().getPersona());
                    movimientoPersonas.setActivo(true);
                    movimientoPersonas.setReferenciaId(ventaCreditoCuota.getId());
                    movimientoPersonas.setTipo(TipoMovimientoPersonas.VENTA_CREDITO);
                    movimientoPersonas.setValorTotal(vc.getValor() * -1);
                }
            }
        }
        enviarNotificacionVentaCredito(e);
        
        return e;
    }
    
    private void enviarNotificacionVentaCredito(VentaCredito ventaCredito) {
        if (ventaCredito != null && ventaCredito.getId() != null && ventaCredito.getSucursalId() != null) {
            new Thread(() -> {
                enviarNotificacionConReintentos("venta-credito", ventaCredito.getId(), ventaCredito.getSucursalId(), () -> {
                    Long personaId = ventaCredito.getCliente() != null && ventaCredito.getCliente().getPersona() != null 
                        ? ventaCredito.getCliente().getPersona().getId() 
                        : null;
                    
                    if (personaId == null) {
                        System.err.println("VentaCredito sin cliente o persona asociada, no se puede enviar notificación");
                        return false;
                    }
                    
                    Double valorTotal = ventaCredito.getValorTotal() != null ? ventaCredito.getValorTotal() : 0.0;
                    
                    String servidorUrl = env.getProperty("servidor.url", "http://159.203.86.103:8081");
                    String url = servidorUrl + "/notification/venta-credito/" 
                        + ventaCredito.getId() + "/" 
                        + ventaCredito.getSucursalId() + "/"
                        + personaId + "/"
                        + valorTotal;
                    
                    restTemplate.postForEntity(url, null, String.class);
                    return true;
                });
            }).start();
        }
    }
    
    /**
     * Envía una notificación al servidor con reintentos y prevención de duplicados
     * @param tipo Tipo de notificación (para logging)
     * @param id ID de la entidad
     * @param sucursalId ID de la sucursal
     * @param notificationTask Tarea que envía la notificación
     */
    private void enviarNotificacionConReintentos(String tipo, Long id, Long sucursalId, java.util.function.Supplier<Boolean> notificationTask) {
        String idempotencyKey = tipo + "-" + id + "-" + sucursalId + "-" + System.currentTimeMillis();
        
        int maxRetries = 3;
        int retryDelay = 1000; 
        
        for (int intento = 1; intento <= maxRetries; intento++) {
            try {
                boolean exito = notificationTask.get();
                if (exito) {
                    return;
                }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 || e.getStatusCode().value() == 408) {
                    if (intento < maxRetries) {
                        try {
                            Thread.sleep(retryDelay);
                        try {
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    } else {
                        System.err.println("Error " + e.getStatusCode().value() + " al enviar notificación " + tipo + " después de " + maxRetries + " intentos");
                        return;
                    }
                } else {
                    System.err.println("Error HTTP " + e.getStatusCode().value() + " al enviar notificación " + tipo + ": " + e.getMessage());
                    return;
                }
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                if (intento < maxRetries) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    System.err.println("Error del servidor al enviar notificación " + tipo + " después de " + maxRetries + " intentos: " + e.getMessage());
                    return;
                }
            } catch (Exception e) {
                if (intento < maxRetries) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    System.err.println("Error al enviar notificación " + tipo + " después de " + maxRetries + " intentos: " + e.getMessage());
                    return;
                }
            }
        }
    }

    public Boolean imprimirVentaCredito(Long id, Long sucId, String printerName) throws GraphQLException {
        VentaCredito ventaCredito = service.findById(id).orElse(null);
        List<VentaCreditoCuota> ventaCreditoCuotaList = ventaCreditoCuotaService.findByVentaCreditoId(ventaCredito.getId());
        Delivery delivery = ventaCredito.getVenta().getDelivery();
        if (ventaCredito == null) throw new GraphQLException("Venta credito no encontrada");
        try {
            printTicket58mm(ventaCredito, ventaCredito.getVenta(), null, printerName, ventaCreditoCuotaList, delivery);
            return true;
        } catch (Exception e) {
            throw new GraphQLException("Error al imprimir");
        }
    }

    public Boolean printTicket58mm(VentaCredito ventaCredito, Venta venta, List<VentaItem> ventaItemList, String printerName, List<VentaCreditoCuota> itens, Delivery delivery) throws Exception {
        Boolean ok = null;
        PrintService selectedPrintService = PrinterOutputStream.getPrintServiceByName(printerName);
        Sucursal sucursal = sucursalService.findById(ventaCredito.getSucursalId()).orElse(null);

        Double descuento = 0.0;
        Double aumento = 0.0;
        Double vueltoGs = 0.0;
        Double vueltoRs = 0.0;
        Double vueltoDs = 0.0;
        Double pagadoGs = 0.0;
        Double pagadoRs = 0.0;
        Double pagadoDs = 0.0;
        Double precioDeliveryGs = 0.0;
        Double precioDeliveryRs = 0.0;
        Double precioDeliveryDs = 0.0;
        Double cambioRs = cambioService.findLastByMonedaId(Long.valueOf(2)).getValorEnGs();
        Double cambioDs = cambioService.findLastByMonedaId(Long.valueOf(3)).getValorEnGs();

        if (delivery != null) {
            precioDeliveryGs = delivery.getPrecio().getValor();
            precioDeliveryRs = precioDeliveryGs / cambioRs;
            precioDeliveryDs = precioDeliveryGs / cambioDs;
        }


        if (selectedPrintService != null) {
            PrinterOutputStream printerOutputStream = new PrinterOutputStream(selectedPrintService);
            // creating the EscPosImage, need buffered image and algorithm.
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            //Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center).setFontSize(Style.FontSize._1, Style.FontSize._1);
            // QRCode qrCode = new QRCode();

            BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
            imageBufferedImage = resize(imageBufferedImage, 200, 100);
            BitImageWrapper imageWrapper = new BitImageWrapper();
            EscPos escpos = null;
            escpos = new EscPos(printerOutputStream);
//            escpos.setPrinterCharacterTable(EscPos.CharacterCodeTable.WPC1252.value);
//            escpos.setCharsetName("UTF-8");
            Bitonal algorithm = new BitonalThreshold();
            EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
            imageWrapper.setJustification(EscPosConst.Justification.Center);
            escpos.writeLF("--------------------------------");
            escpos.write(imageWrapper, escposImage);
            escpos.writeLF(factura, "FRANCO AREVALOS S.A.");
            if (sucursal != null) {
                escpos.writeLF(center, "Suc: " + sucursal.getNombre());
                if (sucursal.getCiudad() != null) {
                    escpos.writeLF(center, sucursal.getCiudad().getDescripcion());
                }
            }
            if (delivery != null) {
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "Modo: Delivery");
                if (delivery.getTelefono() != null) escpos.writeLF("Telefono: " + delivery.getTelefono());
                if (delivery.getDireccion() != null) escpos.writeLF("Direccion: " + delivery.getDireccion());
                escpos.writeLF("--------------------------------");
            }
            escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());

            if (venta.getUsuario().getPersona().getNombre().length() > 23) {
                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre());

            } else {
                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre());
            }

            escpos.writeLF("Fecha: " + venta.getCreadoEn().format(formatter));
            escpos.writeLF("--------------------------------");
            escpos.writeLF("Producto");
            escpos.writeLF("Cant  IVA   P.U              P.T");
            escpos.writeLF("--------------------------------");
            if (ventaItemList == null) {
                ventaItemList = ventaItemService.findByVentaId(venta.getId());
            }
            for (VentaItem vi : ventaItemList) {
                String cantidad = vi.getCantidad().intValue() + " (" + vi.getPresentacion().getCantidad().intValue() + ") " + "10%";
                escpos.writeLF(vi.getProducto().getDescripcion());
                escpos.write(new Style().setBold(true), cantidad);
                String valorUnitario = NumberFormat.getNumberInstance(Locale.GERMAN).format(vi.getPrecioVenta().getPrecio().intValue() - vi.getValorDescuento().intValue());
                String valorTotal = String.valueOf((vi.getPrecioVenta().getPrecio().intValue() - vi.getValorDescuento().intValue()) * vi.getCantidad().intValue());
                for (int i = 14; i > cantidad.length(); i--) {
                    escpos.write(" ");
                }
                escpos.write(valorUnitario);
                for (int i = 16 - valorUnitario.length(); i > valorTotal.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(NumberFormat.getNumberInstance(Locale.GERMAN).format(vi.getPrecioVenta().getPrecio().intValue() * vi.getCantidad().intValue()));
            }
            if (delivery != null) {
                escpos.writeLF("--------------------------------");
                escpos.write("Delivery: ");
                String deliveryGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(precioDeliveryGs.intValue());
                for (int i = 22; i > deliveryGs.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(deliveryGs);
            }
            escpos.writeLF("--------------------------------");
//            escpos.write("Descuento Gs: ");
//            String valorDescuentoGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(descuento.intValue());
//            for (int i = 22; i > valorDescuentoGs.length(); i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF("--------------------------------");
            escpos.write("Total Gs: ");
            String valorGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(venta.getTotalGs().intValue() + precioDeliveryGs.intValue());
            for (int i = 22; i > valorGs.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(valorGs);
            escpos.write("Total Rs: ");
            String valorRs = String.format("%.2f", venta.getTotalRs() + precioDeliveryRs);
            for (int i = 22; i > valorGs.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(valorRs);
            escpos.write("Total Ds: ");
//      String valorDs = NumberFormat.getNumberInstance(new Locale("sk", "SK")).format(venta.getTotalDs());
            String valorDs = String.format("%.2f", venta.getTotalDs() + precioDeliveryDs);
            for (int i = 22; i > valorGs.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(valorDs);
            escpos.writeLF("--------------------------------");
            for (int x = 0; x < itens.size(); x++) {
                escpos.writeLF(center, "PAGARE A LA ORDEN " + x + 1 + "/" + itens.size());
                escpos.feed(1);
                escpos.write("Total Gs: ");
                String valorPagare = NumberFormat.getNumberInstance(Locale.GERMAN).format(itens.get(x).getValor().intValue());
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

                StringBuilder sb = new StringBuilder();
                sb.append("El dia ");
                sb.append(itens.get(x).getVencimiento().format(formatter));
                sb.append(" pagare solidariamente al Sr. FRANCO AREVALOS S.A. la suma de G$ ");
                sb.append(valorPagare);
                sb.append("por el valor recibido a mi/nuestro entera satisfaccion. En caso de retardo o incumplimiento total o parcial a la fecha de su vencimiento quedara contituida la MORA automatica, sin necesidad de interpelacion alguna.");
                escpos.write(sb.toString());
                escpos.feed(4);
                escpos.writeLF("   --------------------------   ");
                escpos.writeLF(center, "FIRMA");
                escpos.writeLF(center, "Deudor: " + venta.getCliente().getPersona().getNombre().toUpperCase());
                escpos.writeLF(center, "RUC: " + venta.getCliente().getPersona().getDocumento() + getDigitoVerificadorString(venta.getCliente().getPersona().getDocumento()));
            }

            escpos.feed(1);
            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
            escpos.feed(5);
            escpos.cut(EscPos.CutMode.FULL);
            try {
                escpos.close();
                printerOutputStream.close();
                ok = true;
            } catch (IOException ioe) {
                ioe.printStackTrace();
                ok = false;
            }
        }
        return ok;
    }
}
}