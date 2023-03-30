package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.TipoConfirmacion;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.dto.VentaPorPeriodoV1Dto;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.graphql.financiero.FacturaLegalItemGraphQL;
import com.franco.dev.graphql.financiero.VentaCreditoGraphQL;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput;
import com.franco.dev.graphql.financiero.input.VentaCreditoInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.reports.TicketReportService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.barcode.QRCode;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.franco.dev.service.utils.PrintingService.resize;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;
import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class VentaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(VentaGraphQL.class);
    @Autowired
    public VentaItemGraphQL ventaItemGraphQL;
    @Autowired
    public CobroGraphQL cobroGraphQL;
    @Autowired
    private VentaService service;
    @Autowired
    private VentaItemService ventaItemService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private FormaPagoService formaPagoService;
    @Autowired
    private PdvCajaService pdvCajaService;
    @Autowired
    private TicketReportService ticketReportService;
    @Autowired
    private ImageService imageService;
    @Autowired
    private SucursalService sucursalService;
    @Autowired
    private MovimientoCajaService movimientoCajaService;

    private PrinterOutputStream printerOutputStream;
    @Autowired
    private ProductoService productoService;
    @Autowired
    private PrintingService printingService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private FacturaService facturaService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private Environment env;

    @Autowired
    private FacturaLegalGraphQL facturaLegalGraphQL;

    @Autowired
    private FacturaLegalItemGraphQL facturaLegalItemGraphQL;

    @Autowired
    private VentaCreditoGraphQL ventaCreditoGraphQL;

    @Autowired
    private CobroService cobroService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private DeliveryService deliveryService;

    private Sucursal sucursal;

    public Optional<Venta> venta(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<Venta> ventas(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

//    public List<Venta> ventaSearch(String texto){
//        return service.findByAll(texto);
//    }

    @Transactional
    public Venta saveVenta(VentaInput ventaInput) {
        ModelMapper m = new ModelMapper();
        Venta e = m.map(ventaInput, Venta.class);
        if (ventaInput.getUsuarioId() != null) e.setUsuario(usuarioService.findById(ventaInput.getUsuarioId()).orElse(null));
        if (ventaInput.getClienteId() != null) {
            e.setCliente(clienteService.findById(ventaInput.getClienteId()).orElse(null));
        } else {
            e.setCliente(clienteService.findById((long) 0).orElse(null));
        }
        if (ventaInput.getFormaPagoId() != null)
            e.setFormaPago(formaPagoService.findById(ventaInput.getFormaPagoId()).orElse(null));
        if (ventaInput.getCajaId() != null) e.setCaja(pdvCajaService.findById(ventaInput.getCajaId()).orElse(null));
        if (ventaInput.getCobroId() != null) e.setCobro(cobroService.findById(ventaInput.getCobroId()).orElse(null));
        e.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        return service.saveAndSend(e, false);
    }

    @Transactional
    public Venta saveVenta(VentaInput ventaInput, List<VentaItemInput> ventaItemList, CobroInput cobroInput, List<CobroDetalleInput> cobroDetalleList, Boolean ticket, String printerName, String local, Long pdvId, VentaCreditoInput ventaCreditoInput, List<VentaCreditoCuotaInput> ventaCreditoCuotaInputList) throws Exception, GraphQLException {
        if(ventaItemList==null && cobroDetalleList == null && cobroDetalleList == null){
            return this.saveVenta(ventaInput);
        }
        Venta venta = null;
        Cobro cobro = cobroGraphQL.saveCobro(cobroInput, cobroDetalleList, ventaInput.getCajaId());
        List<VentaItem> ventaItemList1 = new ArrayList<>();
        if (cobro != null) {
            ModelMapper m = new ModelMapper();
            Venta e = m.map(ventaInput, Venta.class);
            if (e.getUsuario() != null) e.setUsuario(usuarioService.findById(ventaInput.getUsuarioId()).orElse(null));
            if (e.getCliente() != null) {
                e.setCliente(clienteService.findById(ventaInput.getClienteId()).orElse(null));
            } else {
                e.setCliente(clienteService.findById((long) 0).orElse(null));
            }
            if (e.getFormaPago() != null)
                e.setFormaPago(formaPagoService.findById(ventaInput.getFormaPagoId()).orElse(null));
            if (e.getCaja() != null) e.setCaja(pdvCajaService.findById(ventaInput.getCajaId()).orElse(null));
            e.setCobro(cobro);
            e.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
            venta = service.saveAndSend(e, false);
            if (venta != null) {
                ventaItemList1 = ventaItemGraphQL.saveVentaItemList(ventaItemList, venta.getId());
            }
            log.info("Todo guardado");
        }
        if (venta.getId() == null) {
            deshacerVenta(venta, cobro, null);
        } else {
            try {
                if (ticket != null && ticket == true) {

                    if (pdvId != null) {
                        FacturaLegalInput facturaLegalInput = new FacturaLegalInput();
                        if (venta.getCliente() == null) {
                            facturaLegalInput.setNombre("SIN NOMBRE");
                            facturaLegalInput.setRuc("X");
                        } else {
                            facturaLegalInput.setNombre(venta.getCliente().getPersona().getNombre());
                            facturaLegalInput.setRuc(venta.getCliente().getPersona().getDocumento());
                        }
                        facturaLegalInput.setVentaId(venta.getId());
                        facturaLegalInput.setCredito(ventaCreditoInput != null ? true : false);
                        facturaLegalInput.setUsuarioId(ventaInput.getUsuarioId());
                        List<FacturaLegalItemInput> facturaLegalItemInputList = new ArrayList<>();
                        for (VentaItem vi : ventaItemList1) {
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
//                        SaveFacturaDto saveFacturaDto = facturaService.printTicket58mmFactura(venta, facturaLegalInput, facturaLegalItemInputList, printerName, pdvId, false);
                        Boolean facturado = facturaLegalGraphQL.saveFacturaLegal(facturaLegalInput, facturaLegalItemInputList, printerName, pdvId);
                        if (facturado == false) throw new GraphQLException("Problema al generar factura");
                    } else {
                        printTicket58mm(venta, cobro, ventaItemList1, cobroDetalleList, false, printerName, local, false, null, null);
                    }
                    if (ventaCreditoInput != null && ventaCreditoCuotaInputList != null) {
                        ventaCreditoInput.setVentaId(venta.getId());
                        ventaCreditoInput.setSucursalId(venta.getSucursalId());
                        VentaCredito ventaCredito = ventaCreditoGraphQL.saveVentaCredito(ventaCreditoInput, ventaCreditoCuotaInputList);
                        if (ventaCredito != null) {
                            printTicket58mm(venta, cobro, ventaItemList1, cobroDetalleList, false, printerName, local, true, ventaCreditoCuotaInputList, null);
                        }
                    }
                    return venta;
                }

            } catch (Exception e) {
                log.info("retornando venta con exepcion");
                e.printStackTrace();
                return venta;
            }
        }
        log.info("retornando venta sin excepcion");
        return venta;
    }

    public Boolean imprimirPagare(Long ventaId, List<VentaCreditoCuotaInput> itens, String printerName, String local, Long sucId) {
        Venta venta = service.findById(ventaId).orElse(null);
        try {
            if (venta != null) {
                Cobro cobro = cobroGraphQL.cobro(venta.getCobro().getId(), venta.getSucursalId()).orElse(null);
                List<VentaItem> ventaItemList = ventaItemGraphQL.ventaItemListPorVentaId(venta.getId(), venta.getSucursalId());
                if (cobro != null) {
                    List<CobroDetalleInput> cobroDetalleList = new ArrayList<>();
                    printTicket58mm(venta, cobro, ventaItemList, cobroDetalleList, true, printerName, local, true, itens, null);
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public Boolean deleteVenta(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countVenta() {
        return service.count();
    }

    public void deshacerVenta(Venta venta, Cobro cobro, Long sucId) {

        if (cobro != null) {
            cobroGraphQL.deleteCobro(cobro.getId(), null);
        }
    }

//    public void printTest(String printerName) {
//        System.out.println("imprimiendo en la impresora " + printerName);
//        PrintService selectedPrintService = null;
//        File file = null;
//        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(null);
//        Map<String, Object> parameters = new HashMap<>();
//        parameters.put("nombre", "Gabriel");
//        try {
//            System.out.println(imageService.storageDirectoryPathReports+"ticket58.jrxml");
//            file = ResourceUtils.getFile(imageService.storageDirectoryPathReports+"ticket58.jrxml");
//            System.out.println("file es: " + file.getAbsolutePath());
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//        JasperReport jasperReport = null;
//        JasperPrint jasperPrint1 = null;
//        try {
//            System.out.println("Creando el reporte");
//            jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
//            jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());
//            System.out.println("reporte creado, cant de paginas: " + jasperPrint1.getPages().size());
//        } catch (JRException e) {
//            e.printStackTrace();
//        }
//        JRPrintServiceExporter exporter = new JRPrintServiceExporter();
//        SimplePrintServiceExporterConfiguration configuration = new SimplePrintServiceExporterConfiguration();
//        configuration.setDisplayPageDialog(false);
//        configuration.setDisplayPrintDialog(false);
//
//        exporter.setExporterInput(new SimpleExporterInput(jasperPrint1));
//        exporter.setConfiguration(configuration);
//
//        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
//        System.out.println("Number of print services: " + printServices.length);
//        for (PrintService printer : printServices) {
//            System.out.println("Printer: " + printer.getName());
//            if (printer.getName().contains(printerName)) {
//                System.out.println("impresora seleccionada: " + printer.getName());
//                selectedPrintService = printer;
//            }
//        }
//
//        if (selectedPrintService != null) {
//            System.out.println("impresora encontrada, imprimiendo");
//            try {
//                System.out.println("exportando con exporter");
//                exporter.exportReport();
//                System.out.println("exporter " + exporter.getPrintStatus());
////                exporter.
//            } catch (JRException e) {
//                e.printStackTrace();
//            }
//        } else {
//            System.out.println("You did not set the printer!");
//        }
//
//    }

    public Boolean printTicket58mm(Venta venta, Cobro cobro, List<VentaItem> ventaItemList, List<CobroDetalleInput> cobroDetalleList, Boolean reimpresion, String printerName, String local, Boolean pagare, List<VentaCreditoCuotaInput> itens, Delivery delivery) throws Exception {
        Boolean ok = null;
        PrintService selectedPrintService = printingService.getPrintService(printerName);

        if (sucursal == null) {
            sucursal = sucursalService.sucursalActual();
        }

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

        if (cobroDetalleList != null) {
            for (CobroDetalleInput cdi : cobroDetalleList) {
                if (cdi.getAumento()) {
                    aumento += cdi.getValor() * cdi.getCambio();
                }
                if (cdi.getDescuento()) {
                    descuento += cdi.getValor() * cdi.getCambio();
                }
                if (cdi.getVuelto() != null) {
                    if (cdi.getMonedaId() == 1) {
                        vueltoGs = cdi.getValor();
                    }
                    if (cdi.getMonedaId() == 2) {
                        vueltoRs = cdi.getValor();
                    }
                    if (cdi.getMonedaId() == 3) {
                        vueltoDs = cdi.getValor();
                    }
                }
            }
        }


        if (selectedPrintService != null) {
            printerOutputStream = new PrinterOutputStream(selectedPrintService);
            // creating the EscPosImage, need buffered image and algorithm.
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            //Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center).setFontSize(Style.FontSize._1, Style.FontSize._1);
            QRCode qrCode = new QRCode();

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
            if (reimpresion == true) {
                escpos.writeLF(center.setBold(true), "REIMPRESION");
            }
            if (sucursal != null) {
                escpos.writeLF(center, "Suc: " + sucursal.getNombre());
                if (sucursal.getCiudad() != null) {
                    escpos.writeLF(center, sucursal.getCiudad().getDescripcion());
                }
            }
            if (local != null) {
                escpos.writeLF(center, "Local: " + local);
            }
            if (delivery != null) {
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "Modo: Delivery");
                if(delivery.getTelefono()!=null) escpos.writeLF("Telefono: " + delivery.getTelefono());
                if(delivery.getDireccion()!=null) escpos.writeLF("Direccion: " + delivery.getDireccion());
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
            if (pagare != null && pagare == true) {
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
                    if (local != null) {
                        escpos.writeLF(center, "Local: " + local);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("El dia ");
                    sb.append(toDate(itens.get(x).getVencimiento()).format(formatter));
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
            } else {
                if (sucursal != null && sucursal.getNroDelivery() != null) {
                    escpos.write(center, "Delivery? Escanea el codigo qr o escribinos al ");
                    escpos.writeLF(center, sucursal.getNroDelivery());
                }
                if (sucursal.getNroDelivery() != null && sucursal.getNroDelivery().contains("595")) {
                    escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center), "wa.me/" + sucursal.getNroDelivery());
                }
            }

            escpos.feed(1);
            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
            escpos.feed(5);
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


    public List<Venta> ventasPorCajaId(Long id, Integer page, Integer size, Boolean asc, Long sucId, Long formaPago, VentaEstado estado, Boolean isDelivery) {
        return service.findByCajaId(id, sucId, page, size, asc, formaPago, estado, isDelivery);
    }

    public Boolean cancelarVenta(Long id, Long sucId) {
        Venta venta = service.findById(id).orElse(null);
        if (venta != null && venta.getEstado() != VentaEstado.CANCELADA) {
            return service.cancelarVenta(venta);
        }
        return false;
    }

    public Boolean reimprimirVenta(Long id, String printerName, String local, Long sucId) throws Exception {
        Venta venta = service.findById(id).orElse(null);
        if (venta != null) {
            Cobro cobro = cobroGraphQL.cobro(venta.getCobro().getId(), null).orElse(null);
            List<VentaItem> ventaItemList = ventaItemGraphQL.ventaItemListPorVentaId(venta.getId(), null);
            if (cobro != null) {
                List<CobroDetalleInput> cobroDetalleList = new ArrayList<>();
                Delivery delivery = deliveryService.findByVentaId(venta.getId(), venta.getSucursalId());
                printTicket58mm(venta, cobro, ventaItemList, cobroDetalleList, true, printerName, local, null, null, delivery);
                return true;
            }
        }
        return false;
    }

    public List<VentaPorPeriodoV1Dto> ventaPorPeriodo(String inicio, String fin, Long sucId) {
        return service.ventaPorPeriodo(inicio, fin);
    }
}
