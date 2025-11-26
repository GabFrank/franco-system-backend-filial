package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.dto.VentaPorPeriodoV1Dto;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
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
import com.franco.dev.graphql.operaciones.input.DeliveryInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
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
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.util.Locale.GERMAN;

import static com.franco.dev.service.utils.PrintingService.resize;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;
import static com.franco.dev.utilitarios.DateUtils.toDate;
import static com.franco.dev.utilitarios.StringUtils.removeAccents;

@Component
public class VentaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    public static final DecimalFormat df = new DecimalFormat("#,###.##");
    private static final Logger log = LoggerFactory.getLogger(VentaGraphQL.class);
    @Autowired
    public VentaItemGraphQL ventaItemGraphQL;
    @Autowired
    public CobroGraphQL cobroGraphQL;
    Integer facturaCountDown = null;
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
    private Environment env;
    @Autowired
    private FacturaLegalGraphQL facturaLegalGraphQL;
    @Autowired
    private FacturaLegalService facturaLegalService;
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
    @Autowired
    private CobroDetalleGraphQL cobroDetalleGraphQL;
    @Autowired
    private CobroDetalleService cobroDetalleService;
    @Autowired
    private MonedaService monedaService;
    private Sucursal sucursal;

    public Optional<Venta> venta(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<Venta> ventas(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    // public List<Venta> ventaSearch(String texto){
    // return service.findByAll(texto);
    // }

    @Transactional
    public Venta saveVenta2(VentaInput ventaInput) {
        ModelMapper m = new ModelMapper();
        Venta e = m.map(ventaInput, Venta.class);
        if (ventaInput.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(ventaInput.getUsuarioId()).orElse(null));
        if (ventaInput.getClienteId() != null) {
            e.setCliente(clienteService.findById(ventaInput.getClienteId()).orElse(null));
        } else {
            e.setCliente(clienteService.findById((long) 0).orElse(null));
        }
        if (ventaInput.getFormaPagoId() != null)
            e.setFormaPago(formaPagoService.findById(ventaInput.getFormaPagoId()).orElse(null));
        if (ventaInput.getCajaId() != null)
            e.setCaja(pdvCajaService.findById(ventaInput.getCajaId()).orElse(null));
        e.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        return service.saveAndSend(e, false);
    }

    @Transactional
    public Venta saveVenta(VentaInput ventaInput, List<VentaItemInput> ventaItemList, CobroInput cobroInput,
            List<CobroDetalleInput> cobroDetalleList, Boolean ticket, Boolean facturar, String printerName,
            String local, Long pdvId, VentaCreditoInput ventaCreditoInput,
            List<VentaCreditoCuotaInput> ventaCreditoCuotaInputList) throws Exception, GraphQLException {
        if (facturaCountDown == null)
            facturaCountDown = Integer.valueOf(env.getProperty("facturaCountDown"));
        if (ventaItemList == null && cobroDetalleList == null && cobroDetalleList == null) {
            return this.saveVenta2(ventaInput);
        }
        Venta venta = null;
        Cobro cobro = cobroGraphQL.saveCobro(cobroInput, cobroDetalleList, ventaInput.getCajaId());
        List<VentaItem> ventaItemList1 = new ArrayList<>();
        if (cobro != null) {
            ModelMapper m = new ModelMapper();
            Venta e = m.map(ventaInput, Venta.class);
            if (e.getUsuario() != null)
                e.setUsuario(usuarioService.findById(ventaInput.getUsuarioId()).orElse(null));
            if (e.getCliente() != null) {
                e.setCliente(clienteService.findById(ventaInput.getClienteId()).orElse(null));
            } else {
                e.setCliente(clienteService.findById((long) 0).orElse(null));
            }
            if (e.getFormaPago() != null)
                e.setFormaPago(formaPagoService.findById(ventaInput.getFormaPagoId()).orElse(null));
            if (e.getCaja() != null)
                e.setCaja(pdvCajaService.findById(ventaInput.getCajaId()).orElse(null));
            if (e.getCaja().getConteoCierre() != null)
                throw new GraphQLException("Esta caja ya esta cerrada");
            e.setCobro(cobro);
            e.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
            venta = service.saveAndSend(e, false);
            if (venta != null) {
                ventaItemList1 = ventaItemGraphQL.saveVentaItemList(ventaItemList, venta.getId());
                if (venta.getDelivery() != null && venta.getEstado() == VentaEstado.CONCLUIDA
                        && venta.getDelivery().getEstado() != DeliveryEstado.CONCLUIDO) {
                    venta.getDelivery().setEstado(DeliveryEstado.CONCLUIDO);
                    deliveryService.save(venta.getDelivery());
                }
            }
        }
        if (venta.getId() == null) {
            deshacerVenta(venta, cobro, null);
        } else {
            try {
                if (ticket != null && ticket == true) {
                    if (pdvId != null && facturar) {
                        // INICIO: Nueva lógica de facturación
                        
                        // Crear factura legal con documento electrónico integrado (incluye cálculo de descuentos)
                        FacturaLegal facturaLegalConDE = facturaService.crearFacturaLegalDesdeVenta(venta, ventaItemList1, pdvId, cobroDetalleList);

                        // Imprimir el ticket/factura con los datos del DE
                        facturaLegalGraphQL.printTicket58mmFactura(venta, facturaLegalConDE, null, printerName);

                        // FIN: Nueva lógica de facturación

                    } else if (ventaCreditoInput != null && ventaCreditoCuotaInputList != null) {
                        ventaCreditoInput.setVentaId(venta.getId());
                        ventaCreditoInput.setSucursalId(venta.getSucursalId());
                        VentaCredito ventaCredito = ventaCreditoGraphQL.saveVentaCredito(ventaCreditoInput,
                                ventaCreditoCuotaInputList);
                        if (ventaCredito != null) {
                            printTicket58mm(venta, cobro, ventaItemList1, cobroDetalleList, false, printerName, local,
                                    true, ventaCreditoCuotaInputList, null);
                        }
                    } else {
                        printTicket58mm(venta, cobro, ventaItemList1, cobroDetalleList, false, printerName, local,
                                false, null, null);
                    }
                    return venta;
                } else if (facturaCountDown == 0) {
                    if (pdvId != null) {
                        log.info("📋 Facturación silenciosa activada (facturaCountDown == 0) - Generando factura legal con DE sin imprimir");
                        
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
                        
                        // Calcular totales desde CobroDetalle
                        Double totalFinal = venta.getTotalGs();
                        facturaLegalInput.setTotalFinal(totalFinal);
                        
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
                        
                        // Generar factura legal con DE SIN IMPRIMIR (print = false)
                        try {
                            facturaLegalGraphQL.saveFacturaLegal(facturaLegalInput, 
                                facturaLegalItemInputList, printerName, pdvId.intValue(), false);
                            log.info("✅ Factura legal con DE generada exitosamente (sin impresión)");
                        } catch (Exception fe) {
                            log.error("❌ Error en facturación silenciosa: {}", fe.getMessage(), fe);
                            // No lanzamos excepción para no romper el flujo de venta
                        }
                        
                        facturaCountDown = Integer.valueOf(env.getProperty("facturaCountDown"));
                    }
                } else {
                    facturaCountDown = facturaCountDown - 1;
                }

            } catch (Exception e) {
                e.printStackTrace();
                return venta;
            }
        }
        return venta;
    }

    public Boolean imprimirPagare(Long ventaId, List<VentaCreditoCuotaInput> itens, String printerName, String local,
            Long sucId) {
        Venta venta = service.findById(ventaId).orElse(null);
        try {
            if (venta != null) {
                Cobro cobro = cobroGraphQL.cobro(venta.getCobro().getId(), venta.getSucursalId()).orElse(null);
                List<VentaItem> ventaItemList = ventaItemGraphQL.ventaItemListPorVentaId(venta.getId(),
                        venta.getSucursalId());
                if (cobro != null) {
                    List<CobroDetalleInput> cobroDetalleList = new ArrayList<>();
                    printTicket58mm(venta, cobro, ventaItemList, cobroDetalleList, true, printerName, local, true,
                            itens, null);
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

    // public void printTest(String printerName) {
    // System.out.println("imprimiendo en la impresora " + printerName);
    // PrintService selectedPrintService = null;
    // File file = null;
    // JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(null);
    // Map<String, Object> parameters = new HashMap<>();
    // parameters.put("nombre", "Gabriel");
    // try {
    // System.out.println(imageService.storageDirectoryPathReports+"ticket58.jrxml");
    // file =
    // ResourceUtils.getFile(imageService.storageDirectoryPathReports+"ticket58.jrxml");
    // System.out.println("file es: " + file.getAbsolutePath());
    // } catch (FileNotFoundException e) {
    // e.printStackTrace();
    // }
    // JasperReport jasperReport = null;
    // JasperPrint jasperPrint1 = null;
    // try {
    // System.out.println("Creando el reporte");
    // jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
    // jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, new
    // JREmptyDataSource());
    // System.out.println("reporte creado, cant de paginas: " +
    // jasperPrint1.getPages().size());
    // } catch (JRException e) {
    // e.printStackTrace();
    // }
    // JRPrintServiceExporter exporter = new JRPrintServiceExporter();
    // SimplePrintServiceExporterConfiguration configuration = new
    // SimplePrintServiceExporterConfiguration();
    // configuration.setDisplayPageDialog(false);
    // configuration.setDisplayPrintDialog(false);
    //
    // exporter.setExporterInput(new SimpleExporterInput(jasperPrint1));
    // exporter.setConfiguration(configuration);
    //
    // PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null,
    // null);
    // System.out.println("Number of print services: " + printServices.length);
    // for (PrintService printer : printServices) {
    // System.out.println("Printer: " + printer.getName());
    // if (printer.getName().contains(printerName)) {
    // System.out.println("impresora seleccionada: " + printer.getName());
    // selectedPrintService = printer;
    // }
    // }
    //
    // if (selectedPrintService != null) {
    // System.out.println("impresora encontrada, imprimiendo");
    // try {
    // System.out.println("exportando con exporter");
    // exporter.exportReport();
    // System.out.println("exporter " + exporter.getPrintStatus());
    //// exporter.
    // } catch (JRException e) {
    // e.printStackTrace();
    // }
    // } else {
    // System.out.println("You did not set the printer!");
    // }
    //
    // }

    public Boolean printTicket58mm(Venta venta, Cobro cobro, List<VentaItem> ventaItemList,
            List<CobroDetalleInput> cobroDetalleList, Boolean reimpresion, String printerName, String local,
            Boolean pagare, List<VentaCreditoCuotaInput> itens, Delivery delivery) throws Exception {
        Boolean ok = null;
        PrintService selectedPrintService = printingService.getPrintService(printerName);

        if (sucursal == null) {
            sucursal = sucursalService.sucursalActual();
        }

        Double descuento = 0.0;
        Double descuentoRs = 0.0;
        Double descuentoDs = 0.0;
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
        
        // Obtener instancias de las monedas para usar getAbreviatura()
        Moneda monedaGs = monedaService.findById(Long.valueOf(1)).orElse(null);
        Moneda monedaRs = monedaService.findById(Long.valueOf(2)).orElse(null);
        Moneda monedaDs = monedaService.findById(Long.valueOf(3)).orElse(null);

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

        if (descuento > 0) {
            descuentoRs = descuento / cambioRs;
            descuentoDs = descuento / cambioDs;
        }

        if (selectedPrintService != null) {
            printerOutputStream = new PrinterOutputStream(selectedPrintService);
            // creating the EscPosImage, need buffered image and algorithm.
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            // Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center)
                    .setFontSize(Style.FontSize._1, Style.FontSize._1);
            QRCode qrCode = new QRCode();

            BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
            imageBufferedImage = resize(imageBufferedImage, 200, 100);
            BitImageWrapper imageWrapper = new BitImageWrapper();
            EscPos escpos = null;
            escpos = new EscPos(printerOutputStream);
            // escpos.setPrinterCharacterTable(EscPos.CharacterCodeTable.WPC1252.value);
            // escpos.setCharsetName("UTF-8");
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
                if (delivery.getTelefono() != null)
                    escpos.writeLF("Telefono: " + delivery.getTelefono());
                if (delivery.getDireccion() != null)
                    escpos.writeLF("Direccion: " + delivery.getDireccion());
                escpos.writeLF("--------------------------------");
            }
            escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());

            if (venta.getUsuario().getPersona().getNombre().length() > 23) {
                escpos.writeLF("Cajero: " + removeAccents(venta.getUsuario().getPersona().getNombre()));
            } else {
                escpos.writeLF("Cajero: " + removeAccents(venta.getUsuario().getPersona().getNombre()));
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
                String cantidad = df.format(vi.getCantidad().doubleValue()) + " ("
                        + vi.getPresentacion().getCantidad().intValue() + ") " + "10%";
                escpos.writeLF(vi.getProducto().getDescripcion());
                escpos.write(new Style().setBold(true), cantidad);
                String valorUnitario = df
                        .format(vi.getPrecioVenta().getPrecio().intValue() - vi.getValorDescuento().intValue());
                String valorTotal = String.valueOf(
                        df.format((vi.getPrecioVenta().getPrecio().intValue() - vi.getValorDescuento().intValue())
                                * vi.getCantidad().doubleValue()));
                for (int i = 14; i > cantidad.length(); i--) {
                    escpos.write(" ");
                }
                escpos.write(valorUnitario);
                for (int i = 18 - valorUnitario.length(); i > valorTotal.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTotal);
            }
            if (delivery != null) {
                escpos.writeLF("--------------------------------");
                escpos.write("Delivery: ");
                String deliveryGs = df.format(precioDeliveryGs.intValue());
                for (int i = 22; i > deliveryGs.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(deliveryGs);
            }
            // escpos.writeLF("--------------------------------");
            
            // Sección de descuentos y totales comentada - ahora se muestra por moneda abajo
            // if (descuento > 0) {
            //     escpos.write("Descuento Gs: ");
            //     String valorDescuentoGs = df.format(descuento.intValue());
            //     for (int i = 18; i > valorDescuentoGs.length(); i--) {
            //         escpos.write(" ");
            //     }
            //     escpos.writeLF(valorDescuentoGs);
            //     escpos.write("Descuento Rs: ");
            //     String valorDescuentoRs = String.format("%.2f", descuentoRs);
            //     for (int i = 18; i > valorDescuentoRs.length(); i--) {
            //         escpos.write(" ");
            //     }
            //     escpos.writeLF(valorDescuentoRs);
            //     escpos.write("Descuento Ds: ");
            //     String valorDescuentoDs = String.format("%.2f", descuentoDs);
            //     for (int i = 18; i > valorDescuentoDs.length(); i--) {
            //         escpos.write(" ");
            //     }
            //     escpos.writeLF(valorDescuentoDs);
            //     escpos.writeLF("--------------------------------");
            // }
            // escpos.write("Total Gs: ");
            // String valorGs = df
            //         .format(venta.getTotalGs().intValue() + precioDeliveryGs.intValue() - descuento.intValue());
            // for (int i = 22; i > valorGs.length(); i--) {
            //     escpos.write(" ");
            // }
            // escpos.writeLF(valorGs);
            // escpos.write("Total Rs: ");
            // String valorRs = String.format("%.2f", venta.getTotalRs() + precioDeliveryRs - descuentoRs);
            // for (int i = 22; i > valorGs.length(); i--) {
            //     escpos.write(" ");
            // }
            // escpos.writeLF(valorRs);
            // escpos.write("Total Ds: ");
            // String valorDs = String.format("%.2f", venta.getTotalDs() + precioDeliveryDs - descuentoDs);
            // for (int i = 22; i > valorGs.length(); i--) {
            //     escpos.write(" ");
            // }
            // escpos.writeLF(valorDs);
            // escpos.writeLF("--------------------------------");
            
            // Nueva sección de totales por moneda
            escpos.writeLF("------------Totales-------------");
            // Header: 4 (moneda) + 9 (Parcial) + 9 (Desc.) + 10 (Final) = 32
            escpos.write("   "); // 3 espacios para moneda
            escpos.write("   Parcial"); // 10 chars
            escpos.write("    "); // 4 espacios
            escpos.write("Desc."); // 5 chars
            escpos.write("     "); // 5 espacios
            escpos.writeLF("Final"); // 5 chars
            
            // Calcular totales por moneda
            Double totalParcialGs = venta.getTotalGs() + precioDeliveryGs;
            Double totalFinalGs = totalParcialGs - descuento;
            Double totalParcialRs = totalParcialGs / cambioRs;
            Double totalParcialDs = totalParcialGs / cambioDs;
            
            Double descuentoRsCalc = descuento / cambioRs;
            Double descuentoDsCalc = descuento / cambioDs;
            
            Double totalFinalRs = totalFinalGs / cambioRs;
            Double totalFinalDs = totalFinalGs / cambioDs;
            
            // Línea de Guaraníes
            escpos.write(monedaGs != null ? monedaGs.getAbreviatura() + ". " : "Gs. ");
            String parcialGsStr = NumberFormat.getNumberInstance(GERMAN).format(totalParcialGs.intValue());
            int espaciosParcialGs = 9 - parcialGsStr.length();
            for (int i = 0; i < espaciosParcialGs; i++) {
                escpos.write(" ");
            }
            escpos.write(parcialGsStr);
            
            String descGsStr = NumberFormat.getNumberInstance(GERMAN).format(descuento.intValue());
            int espaciosDescGs = 9 - descGsStr.length();
            for (int i = 0; i < espaciosDescGs; i++) {
                escpos.write(" ");
            }
            escpos.write(descGsStr);
            
            String finalGsStr = NumberFormat.getNumberInstance(GERMAN).format(totalFinalGs.intValue());
            int espaciosFinalGs = 10 - finalGsStr.length();
            for (int i = 0; i < espaciosFinalGs; i++) {
                escpos.write(" ");
            }
            escpos.writeLF(finalGsStr);
            
            // Línea de Reales
            escpos.write(monedaRs != null ? monedaRs.getAbreviatura() + ". " : "Rs. ");
            String parcialRsStr = String.format(GERMAN, "%.2f", totalParcialRs);
            int espaciosParcialRs = 9 - parcialRsStr.length();
            for (int i = 0; i < espaciosParcialRs; i++) {
                escpos.write(" ");
            }
            escpos.write(parcialRsStr);
            
            String descRsStr = String.format(GERMAN, "%.2f", descuentoRsCalc);
            int espaciosDescRs = 9 - descRsStr.length();
            for (int i = 0; i < espaciosDescRs; i++) {
                escpos.write(" ");
            }
            escpos.write(descRsStr);
            
            String finalRsStr = String.format(GERMAN, "%.2f", totalFinalRs);
            int espaciosFinalRs = 10 - finalRsStr.length();
            for (int i = 0; i < espaciosFinalRs; i++) {
                escpos.write(" ");
            }
            escpos.writeLF(finalRsStr);
            
            // Línea de Dólares
            escpos.write(monedaDs != null ? monedaDs.getAbreviatura() + ". " : "Us. ");
            String parcialDsStr = String.format(GERMAN, "%.2f", totalParcialDs);
            int espaciosParcialDs = 9 - parcialDsStr.length();
            for (int i = 0; i < espaciosParcialDs; i++) {
                escpos.write(" ");
            }
            escpos.write(parcialDsStr);
            
            String descDsStr = String.format(GERMAN, "%.2f", descuentoDsCalc);
            int espaciosDescDs = 9 - descDsStr.length();
            for (int i = 0; i < espaciosDescDs; i++) {
                escpos.write(" ");
            }
            escpos.write(descDsStr);
            
            String finalDsStr = String.format(GERMAN, "%.2f", totalFinalDs);
            int espaciosFinalDs = 10 - finalDsStr.length();
            for (int i = 0; i < espaciosFinalDs; i++) {
                escpos.write(" ");
            }
            escpos.writeLF(finalDsStr);
            
            // Agregar detalles de pago por moneda
            if (cobro != null) {
                List<CobroDetalle> cobroDetalles = cobroDetalleService.findByCobroId(cobro.getId());
                
                // Filtrar solo pagos y vueltos, ignorar descuentos y aumentos
                List<CobroDetalle> pagosYVueltos = new ArrayList<>();
                for (CobroDetalle cd : cobroDetalles) {
                    if ((cd.getPago() != null && cd.getPago()) || (cd.getVuelto() != null && cd.getVuelto())) {
                        pagosYVueltos.add(cd);
                    }
                }
                
                if (!pagosYVueltos.isEmpty()) {
                    // Agrupar por moneda
                    Map<Long, List<CobroDetalle>> porMoneda = new LinkedHashMap<>();
                    for (CobroDetalle cd : pagosYVueltos) {
                        Long monedaId = cd.getMoneda().getId();
                        if (!porMoneda.containsKey(monedaId)) {
                            porMoneda.put(monedaId, new ArrayList<>());
                        }
                        porMoneda.get(monedaId).add(cd);
                    }
                    
                    escpos.writeLF("------------Detalles------------");
                    // Header con columnas Pago y Vuelto (total 32 caracteres)
                    // Distribución: 4 (moneda) + 18 (pago) + 10 (vuelto) = 32
                    escpos.write("          ");  // espacios para columna moneda (4 chars)
                    escpos.write("   Pago");  // (4 chars)
                    for (int i = 0; i < 9; i++) {  // espacios para completar columna pago (10 chars)
                        escpos.write(" ");
                    }
                    escpos.writeLF("Vuelto");  // (6 chars)
                    
                    // Procesar cada moneda
                    for (Map.Entry<Long, List<CobroDetalle>> entry : porMoneda.entrySet()) {
                        List<CobroDetalle> detallesMoneda = entry.getValue();
                        
                        // Agrupar pagos por forma de pago para esta moneda
                        Map<Long, CobroDetalle> pagosPorFormaPago = new LinkedHashMap<>();
                        Double vueltoTotal = 0.0;
                        
                        for (CobroDetalle cd : detallesMoneda) {
                            if (cd.getPago() != null && cd.getPago()) {
                                Long formaPagoId = cd.getFormaPago() != null ? cd.getFormaPago().getId() : 0L;
                                pagosPorFormaPago.put(formaPagoId, cd);
                            } else if (cd.getVuelto() != null && cd.getVuelto()) {
                                vueltoTotal += cd.getValor();
                            }
                        }
                        
                        // Imprimir cada pago
                        // Distribución de 32 caracteres: 4 (moneda) + 18 (pago) + 10 (vuelto)
                        int lineasPago = 0;
                        for (Map.Entry<Long, CobroDetalle> pagoEntry : pagosPorFormaPago.entrySet()) {
                            CobroDetalle pago = pagoEntry.getValue();
                            String simboloMoneda = pago.getMoneda() != null ? pago.getMoneda().getAbreviatura() + "." : "N/A";
                            
                            // Obtener abreviatura de forma de pago
                            String abrevFormaPago = pago.getFormaPago() != null ? pago.getFormaPago().getAbreviatura() : "N/A";
                            
                            // Formatear valor del pago
                            String valorPagoStr;
                            if (pago.getMoneda().getId() == 1) { // Guaraníes
                                valorPagoStr = NumberFormat.getNumberInstance(GERMAN).format(pago.getValor().intValue());
                            } else { // Otras monedas (con decimales)
                                valorPagoStr = String.format(GERMAN, "%.2f", pago.getValor());
                            }
                            valorPagoStr = valorPagoStr + " (" + abrevFormaPago + ")";
                            
                            // Columna 1: Moneda (4 caracteres)
                            escpos.write(simboloMoneda);
                            for (int i = simboloMoneda.length(); i < 4; i++) {
                                escpos.write(" ");
                            }
                            
                            // Columna 2: Pago (18 caracteres, alineado a derecha)
                            int espaciosPago = 18 - valorPagoStr.length();
                            for (int i = 0; i < espaciosPago; i++) {
                                escpos.write(" ");
                            }
                            escpos.write(valorPagoStr);
                            
                            // Columna 3: Vuelto (10 caracteres, alineado a derecha)
                            if (lineasPago == 0) {
                                String valorVueltoStr;
                                if (vueltoTotal != 0) {
                                    // Usar valor absoluto del vuelto (puede venir negativo de la BD)
                                    Double vueltoAbsoluto = Math.abs(vueltoTotal);
                                    if (pago.getMoneda().getId() == 1) { // Guaraníes
                                        valorVueltoStr = NumberFormat.getNumberInstance(GERMAN).format(vueltoAbsoluto.intValue());
                                    } else { // Otras monedas (con decimales)
                                        valorVueltoStr = String.format(GERMAN, "%.2f", vueltoAbsoluto);
                                    }
                                } else {
                                    valorVueltoStr = "0";
                                }
                                int espaciosVuelto = 10 - valorVueltoStr.length();
                                for (int i = 0; i < espaciosVuelto; i++) {
                                    escpos.write(" ");
                                }
                                escpos.writeLF(valorVueltoStr);
                            } else {
                                // Para líneas subsiguientes, dejar vacío
                                escpos.writeLF("");
                            }
                            
                            lineasPago++;
                        }
                        
                        // Si solo hay vuelto sin pago (caso raro pero por si acaso)
                        if (pagosPorFormaPago.isEmpty() && vueltoTotal != 0) {
                            CobroDetalle primerDetalle = detallesMoneda.get(0);
                            String simboloMoneda = primerDetalle.getMoneda() != null ? primerDetalle.getMoneda().getAbreviatura() + "." : "N/A";
                            
                            // Columna 1: Moneda (4 caracteres)
                            escpos.write(simboloMoneda);
                            for (int i = simboloMoneda.length(); i < 4; i++) {
                                escpos.write(" ");
                            }
                            
                            // Columna 2: Pago vacía (18 caracteres)
                            for (int i = 0; i < 18; i++) {
                                escpos.write(" ");
                            }
                            
                            // Columna 3: Vuelto (10 caracteres, alineado a derecha)
                            // Usar valor absoluto del vuelto (puede venir negativo de la BD)
                            Double vueltoAbsoluto = Math.abs(vueltoTotal);
                            String valorVueltoStr;
                            if (primerDetalle.getMoneda().getId() == 1) {
                                valorVueltoStr = NumberFormat.getNumberInstance(GERMAN).format(vueltoAbsoluto.intValue());
                            } else {
                                valorVueltoStr = String.format(GERMAN, "%.2f", vueltoAbsoluto);
                            }
                            int espaciosVuelto = 10 - valorVueltoStr.length();
                            for (int i = 0; i < espaciosVuelto; i++) {
                                escpos.write(" ");
                            }
                            escpos.writeLF(valorVueltoStr);
                        }
                    }
                }
            }
            
            escpos.writeLF("--------------------------------");
            if (pagare != null && pagare == true) {
                for (int x = 0; x < itens.size(); x++) {
                    escpos.writeLF(center, "PAGARE A LA ORDEN " + x + 1 + "/" + itens.size());
                    escpos.feed(1);
                    escpos.write("Total Gs: ");
                    String valorPagare = df.format(itens.get(x).getValor().intValue());
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
                    sb.append(
                            "por el valor recibido a mi/nuestro entera satisfaccion. En caso de retardo o incumplimiento total o parcial a la fecha de su vencimiento quedara contituida la MORA automatica, sin necesidad de interpelacion alguna.");
                    escpos.write(sb.toString());
                    escpos.feed(4);
                    escpos.writeLF("   --------------------------   ");
                    escpos.writeLF(center, "FIRMA");
                    escpos.writeLF(center, "Deudor: " + venta.getCliente().getPersona().getNombre().toUpperCase());
                    escpos.writeLF(center, "RUC: " + venta.getCliente().getPersona().getDocumento()
                            + getDigitoVerificadorString(venta.getCliente().getPersona().getDocumento()));
                }
            } else {
                if (sucursal != null && sucursal.getNroDelivery() != null) {
                    escpos.write(center, "Delivery? Escanea el codigo qr o escribinos al ");
                    escpos.writeLF(center, sucursal.getNroDelivery());
                }
                if (sucursal.getNroDelivery() != null && sucursal.getNroDelivery().contains("595")) {
                    escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center),
                            "wa.me/" + sucursal.getNroDelivery());
                }
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

    @Transactional
     public Venta saveVentaDelivery(VentaInput ventaInput, DeliveryInput deliveryInput, List<CobroDetalleInput> cobroDetalleList, VentaCreditoInput ventaCreditoInput, List<VentaCreditoCuotaInput> ventaCreditoCuotaInputList) {
         try {
             Venta venta = service.findById(ventaInput.getId()).orElse(null);
             Delivery delivery = deliveryService.findById(deliveryInput.getId()).orElse(null);
             if (venta == null || delivery == null) throw new GraphQLException("Ocurrio un problema.");
             venta.setEstado(VentaEstado.CONCLUIDA);
             venta.setSucursalId(delivery.getSucursalId());
             venta.setUsuario(delivery.getUsuario());
             delivery.setEstado(DeliveryEstado.CONCLUIDO);
             delivery.setFechaConcluido(LocalDateTime.now());
             for (CobroDetalleInput cd : cobroDetalleList) {
                 cd.setCobroId(venta.getCobro().getId());
                 cd.setUsuarioId(delivery.getUsuario().getId());
                 cd.setSucursalId(delivery.getSucursalId());
                 cobroDetalleGraphQL.saveCobroDetalle(cd);
             }
             if (ventaCreditoInput != null) {
                 ventaCreditoInput.setVentaId(venta.getId());
                 VentaCredito ventaCredito = ventaCreditoGraphQL.saveVentaCredito(ventaCreditoInput, ventaCreditoCuotaInputList);
                 venta.setCliente(ventaCredito.getCliente());
             }
             deliveryService.save(delivery);
             return service.save(venta);
         } catch (Exception e) {
             e.printStackTrace();
             throw new GraphQLException("Ocurrio un problema, favor contactar con RRHH");
         }
     }

    public Page<Venta> ventasPorCajaId(Long idVenta, Long idCaja, Integer page, Integer size, Boolean asc, Long sucId,
            Long formaPago, VentaEstado estado, Boolean isDelivery, Long monedaId, Boolean conDescuento) {
        if (idVenta != null) {
            Venta venta = service.findById(idVenta).orElse(null);
            return new PageImpl<>(Arrays.asList(venta), PageRequest.of(0, 1), 1);
        }
        return service.findByCajaId(new EmbebedPrimaryKey(idCaja, sucId), page, size, asc, formaPago, estado,
                isDelivery, monedaId);
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
            FacturaLegal facturaLegal = facturaLegalService.findByVentaId(venta.getId());
            if (facturaLegal != null) {
                facturaLegalGraphQL.reimprimirFacturaLegal(facturaLegal.getId(), sucId, printerName);
            } else {
                Cobro cobro = cobroGraphQL.cobro(venta.getCobro().getId(), null).orElse(null);
                List<VentaItem> ventaItemList = ventaItemGraphQL.ventaItemListPorVentaId(venta.getId(), null);
                if (cobro != null) {
                    List<CobroDetalleInput> cobroDetalleList = new ArrayList<>();
                    Delivery delivery = venta.getDelivery();
                    printTicket58mm(venta, cobro, ventaItemList, cobroDetalleList, true, printerName, local, null, null,
                            delivery);
                    return true;
                }
            }

        }
        return false;
    }

    public List<VentaPorPeriodoV1Dto> ventaPorPeriodo(String inicio, String fin, Long sucId) {
        return service.ventaPorPeriodo(inicio, fin);
    }
}
