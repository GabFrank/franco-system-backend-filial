package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.PuntoDeVenta;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.rabbit.dto.SaveFacturaDto;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.PuntoDeVentaService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.utilitarios.NumeroALetrasService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.barcode.QRCode;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ResourceUtils;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.franco.dev.service.impresion.ImpresionService.shortDateTime;
import static com.franco.dev.service.utils.PrintingService.resize;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;
import static com.franco.dev.utilitarios.DateUtils.dateToStringShort;

@Component
public class FacturaLegalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FacturaLegalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private FacturaLegalItemGraphQL facturaLegalItemGraphQL;

    private PrintService printService;

    private PrinterOutputStream printerOutputStream;

    @Autowired
    private ImageService imageService;

    @Autowired
    private NumeroALetrasService numeroALetrasService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private PuntoDeVentaService puntoDeVentaService;

    @Autowired
    private TimbradoDetalleService timbradoDetalleService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private FacturaService facturaService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private FacturaLegalItemService facturaLegalItemService;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private DeliveryService deliveryService;

    public Optional<FacturaLegal> facturaLegal(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<FacturaLegal> facturaLegales(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Boolean saveFacturaLegal(FacturaLegalInput input, List<FacturaLegalItemInput> facturaLegalItemInputList, String printerName, Long pdvId, Boolean print) {
        if (print == null) print = true;
        Venta venta = input.getVentaId() != null ? ventaService.findById(input.getVentaId()).orElse(null) : null;
        SaveFacturaDto saveFacturaDto = generarFacturaAutoImpreso(venta, input, facturaLegalItemInputList, printerName, pdvId, false, print);
        if (saveFacturaDto.getFacturaLegalInput().getTimbradoDetalleId() == null) {
            return false;
        }
        input = saveFacturaDto.getFacturaLegalInput();
        facturaLegalItemInputList = saveFacturaDto.getFacturaLegalItemInputList();
        ModelMapper m = new ModelMapper();
        FacturaLegal e = m.map(input, FacturaLegal.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getVentaId() != null) e.setVenta(ventaService.findById(input.getVentaId()).orElse(null));
        if (input.getCajaId() != null) e.setCaja(pdvCajaService.findById(input.getCajaId()).orElse(null));
        if (input.getTimbradoDetalleId() != null)
            e.setTimbradoDetalle(timbradoDetalleService.findById(input.getTimbradoDetalleId()).orElse(null));
        if (input.getClienteId() != null) e.setCliente(clienteService.findById(input.getClienteId()).orElse(null));
        if (input.getRuc() != null && input.getRuc() != "X" && !input.getRuc().contains("-")) {
            e.setRuc(input.getRuc() + getDigitoVerificadorString(input.getRuc()));
        }
        e = service.saveAndSend(e, false);
        if (e != null) {
            for (FacturaLegalItemInput c : facturaLegalItemInputList) {
                c.setFacturaLegalId(e.getId());
                facturaLegalItemGraphQL.saveFacturaLegalItem(c);
            }
            return true;
        } else {
            return false;
        }
    }

    public Boolean deleteFacturaLegal(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countFacturaLegal() {
        return service.count();
    }

    public SaveFacturaDto generarFacturaAutoImpreso(Venta venta, FacturaLegalInput facturaLegal, List<FacturaLegalItemInput> facturaLegalItemList, String printerName, Long pdvId, Boolean continuar, Boolean print) {
        PuntoDeVenta puntoDeVenta = puntoDeVentaService.getPuntoDeVentaActual(pdvId);
        TimbradoDetalle timbradoDetalle = puntoDeVenta != null ? timbradoDetalleService.getTimbradoDetalleActual(puntoDeVenta.getId()) : null;
        if (timbradoDetalle.getNumeroActual() >= timbradoDetalle.getRangoHasta()) {
            if (print) {
                throw new GraphQLException("Timbrado detalle fuera de rango. Contactar con RRHH");
            }
        }
        Boolean isBefore = LocalDateTime.now().isAfter(timbradoDetalle.getTimbrado().getFechaFin());
        if (isBefore) {
            if (print) {
                throw new GraphQLException("Timbrado ha vencido. Contactar con RRHH");
            }
        }
        if (timbradoDetalle != null) {
            try {
                return facturaService.printTicket58mmFactura(venta, facturaLegal, facturaLegalItemList, printerName, pdvId, continuar, null, print);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public FacturaDto crearFacturaDto(FacturaLegalInput facturaLegal, List<FacturaLegalItemInput> facturaLegalItemList) {
        FacturaDto facturaDto = new FacturaDto();
        if (facturaLegal.getCredito()) {
            facturaDto.setCredito("X");
            facturaDto.setContado("");

        } else {
            facturaDto.setContado("X");
            facturaDto.setCredito("");

        }
        if (facturaLegal.getNombre() != null) {
            if (facturaLegal.getNombre() != null) facturaDto.setNombre(facturaLegal.getNombre());
            if (facturaLegal.getRuc() != null) facturaDto.setRuc(facturaLegal.getRuc());
            if (facturaLegal.getDireccion() != null) {
                facturaDto.setDireccion(facturaLegal.getDireccion());
            } else {
                facturaDto.setDireccion("");
            }
        }
        facturaDto.setFecha(dateToStringShort(LocalDateTime.now()));
        Double totalIva10 = 0.0;
        Double totalIva5 = 0.0;
        Double totalIva = 0.0;
        Double totalFinal = 0.0;
        String totalEnLetras = "";
        List<VentaItemDto> ventaItemDtoList = new ArrayList<>();
        List<FacturaLegalItemInput> auxList = new ArrayList<>();

        if (facturaLegalItemList.size() > 8) {
            auxList = facturaLegalItemList.subList(8, facturaLegalItemList.size());
            facturaLegalItemList = facturaLegalItemList.subList(0, 8);
        }

        for (FacturaLegalItemInput fi : facturaLegalItemList) {
            if (fi.getIva() == 10) {
                totalIva10 += fi.getTotal() / 11;
            } else if (fi.getIva() == 5) {
                totalIva5 += fi.getTotal() / 21;
            }
            totalFinal += fi.getTotal();
            ventaItemDtoList.add(new VentaItemDto(NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getCantidad().intValue()), fi.getDescripcion(), NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getPrecioUnitario().intValue()), NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getTotal().intValue())));
        }

        totalIva = totalIva10 + totalIva5;
        totalEnLetras = numeroALetrasService.converter(totalFinal.intValue() + "", true);

        facturaDto.setTotalParcial(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal.intValue()));
        facturaDto.setTotal(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal.intValue()));
        facturaDto.setIvaParcial0(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva10.intValue()));
        facturaDto.setIvaParcial5(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva5.intValue()));
        facturaDto.setIvaFinal(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva.intValue()));
        facturaDto.setTotalEnLetras(totalEnLetras);
        facturaDto.setFacturaLegalItemInputList(facturaLegalItemList);
        facturaDto.setFacturaLegalInput(facturaLegal);
//        facturaDto.setVenta(ventaService.findById(facturaLegal.getVentaId()));
        return facturaDto;
    }


    public void generarFactura(FacturaLegalInput facturaLegal, List<FacturaLegalItemInput> facturaLegalItemList, String printerName) {
        try {
            FacturaDto facturaDto = new FacturaDto();
            if (facturaLegal.getCredito()) {
                facturaDto.setCredito("X");
                facturaDto.setContado("");

            } else {
                facturaDto.setContado("X");
                facturaDto.setCredito("");

            }
            if (facturaLegal.getNombre() != null) {
                if (facturaLegal.getNombre() != null) facturaDto.setNombre(facturaLegal.getNombre());
                if (facturaLegal.getRuc() != null) facturaDto.setRuc(facturaLegal.getRuc());
                if (facturaLegal.getDireccion() != null) {
                    facturaDto.setDireccion(facturaLegal.getDireccion());
                } else {
                    facturaDto.setDireccion("");
                }
            }
            facturaDto.setFecha(dateToStringShort(LocalDateTime.now()));
            Double totalIva10 = 0.0;
            Double totalIva5 = 0.0;
            Double totalIva = 0.0;
            Double totalFinal = 0.0;
            String totalEnLetras = "";
            List<VentaItemDto> ventaItemDtoList = new ArrayList<>();
            List<FacturaLegalItemInput> auxList = new ArrayList<>();

            if (facturaLegalItemList.size() > 8) {
                auxList = facturaLegalItemList.subList(8, facturaLegalItemList.size());
                facturaLegalItemList = facturaLegalItemList.subList(0, 8);
            }

            for (FacturaLegalItemInput fi : facturaLegalItemList) {
                if (fi.getIva() == 10) {
                    totalIva10 += fi.getTotal() / 11;
                } else if (fi.getIva() == 5) {
                    totalIva5 += fi.getTotal() / 21;
                }
                totalFinal += fi.getTotal();
                ventaItemDtoList.add(new VentaItemDto(NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getCantidad().intValue()), fi.getDescripcion(), NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getPrecioUnitario().intValue()), NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getTotal().intValue())));
            }

            totalIva = totalIva10 + totalIva5;
            totalEnLetras = numeroALetrasService.converter(totalFinal.intValue() + "", true);

            facturaDto.setTotalParcial(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal.intValue()));
            facturaDto.setTotal(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal.intValue()));
            facturaDto.setIvaParcial0(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva10.intValue()));
            facturaDto.setIvaParcial5(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva5.intValue()));
            facturaDto.setIvaFinal(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva10.intValue() + totalIva5.intValue()));
            facturaDto.setTotalEnLetras(totalEnLetras);

            File file = ResourceUtils.getFile("classpath:factura.jrxml");
            JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(ventaItemDtoList);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("contado", facturaDto.getContado());
            parameters.put("credito", facturaDto.getCredito());
            parameters.put("fecha", facturaDto.getFecha());
            parameters.put("ivaTotal10", facturaDto.getIvaParcial10());
            parameters.put("ivaTotal5", facturaDto.getIvaParcial5());
            parameters.put("ivaTotal", facturaDto.getIvaFinal());
            parameters.put("nombre", facturaDto.getNombre());
            parameters.put("ruc", facturaDto.getRuc());
            parameters.put("totalFinal", facturaDto.getTotal());
            parameters.put("totalEnLetras", facturaDto.getTotalEnLetras());
            parameters.put("direccion", facturaDto.getDireccion());

            JasperPrint jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            jasperPrint1.setPageHeight(842);

            File file2 = ResourceUtils.getFile("classpath:factura2.jrxml");
            JasperReport jasperReport2 = JasperCompileManager.compileReport(file.getAbsolutePath());
            JRBeanCollectionDataSource dataSource2 = new JRBeanCollectionDataSource(ventaItemDtoList);
            Map<String, Object> parameters2 = new HashMap<>();
            parameters2.put("contado", facturaDto.getContado());
            parameters2.put("credito", facturaDto.getCredito());
            parameters2.put("fecha", facturaDto.getFecha());
            parameters2.put("ivaTotal", facturaDto.getIvaFinal());
            parameters2.put("nombre", facturaDto.getNombre());
            parameters2.put("ruc", facturaDto.getRuc());
            parameters2.put("totalFinal", facturaDto.getTotal());
            parameters2.put("totalEnLetras", facturaDto.getTotalEnLetras());
            parameters2.put("direccion", facturaDto.getDireccion());

            JasperPrint jasperPrint2 = JasperFillManager.fillReport(jasperReport2, parameters2, dataSource2);

            JRPrintPage page2 = jasperPrint2.getPages().get(0);
            List<JRPrintElement> elements = page2.getElements();

            for (JRPrintElement e : elements) {
                e.setY(e.getY() + 403);
                jasperPrint1.getPages().get(0).addElement(e);
            }

//            OutputStream output;
//            output = new FileOutputStream(new File("/Users/gabfranck/Desktop/prueba.pdf"));
//            JasperExportManager.exportReportToPdfStream(jasperPrint1, output);

            printFactura(jasperPrint1, printerName);
            if (auxList.size() > 0)
                generarFactura(facturaLegal, auxList, printerName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printFactura(JasperPrint jasperPrint, String printerName) throws GraphQLException {
        PrintRequestAttributeSet printRequestAttributeSet = new HashPrintRequestAttributeSet();
        printRequestAttributeSet.add(MediaSizeName.ISO_A4);
        if (jasperPrint.getOrientationValue() == net.sf.jasperreports.engine.type.OrientationEnum.LANDSCAPE) {
            printRequestAttributeSet.add(OrientationRequested.LANDSCAPE);
        } else {
            printRequestAttributeSet.add(OrientationRequested.PORTRAIT);
        }

        JRPrintServiceExporter exporter = new JRPrintServiceExporter();
        SimplePrintServiceExporterConfiguration configuration = new SimplePrintServiceExporterConfiguration();
        configuration.setPrintRequestAttributeSet(printRequestAttributeSet);
        configuration.setDisplayPageDialog(false);
        configuration.setDisplayPrintDialog(false);

        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setConfiguration(configuration);

        printService = PrinterOutputStream.getPrintServiceByName(printerName);


        if (printService != null) {
            try {
                exporter.exportReport();
            } catch (JRException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("You did not set the printer!");
        }
    }

    public void generarFacturaAutoImpreso(Venta venta, Cobro cobro, List<VentaItem> ventaItemList, List<CobroDetalleInput> cobroDetalleList, Boolean reimpresion, String printerName, String local, FacturaLegalInput facturaLegal) throws Exception {
        Sucursal sucursal = null;
        PrintService selectedPrintService = null;
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService printer : printServices) {
            if (printer.getName().equals(printerName)) {
                selectedPrintService = printer;
            }
        }

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
        for (CobroDetalleInput cdi : cobroDetalleList) {
            if (cdi.getAumento()) {
                aumento += cdi.getValor() * cdi.getCambio();
            }
            if (cdi.getDescuento()) {
                aumento += cdi.getValor() * cdi.getCambio();
            }
            if (cdi.getVuelto()) {
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

        if (selectedPrintService != null) printerOutputStream = new PrinterOutputStream(selectedPrintService);

        // creating the EscPosImage, need buffered image and algorithm.
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        //Styles
        Style center = new Style().setJustification(EscPosConst.Justification.Center);

        QRCode qrCode = new QRCode();

        BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
        imageBufferedImage = resize(imageBufferedImage, 200, 100);
        BitImageWrapper imageWrapper = new BitImageWrapper();
        EscPos escpos = null;
        escpos = new EscPos(printerOutputStream);
        Bitonal algorithm = new BitonalThreshold();
        EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
        imageWrapper.setJustification(EscPosConst.Justification.Center);
        escpos.feed(5);
        escpos.write(imageWrapper, escposImage);
        escpos.writeLF(center, "Av. Paraguay c/ 30 de julio");
        escpos.writeLF(center, "Salto del Guairá");
        if (reimpresion == true) {
            escpos.writeLF(center.setBold(true), "REIMPRESION");
        }
        if (sucursal != null) {
            escpos.writeLF(center, "Suc: " + sucursal.getNombre());
        }
        if (local != null) {
            escpos.writeLF(center, "Local: " + local);
        }
        escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());

        if (venta.getUsuario().getPersona().getNombre().length() > 23) {
            escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre().substring(0, 23));

        } else {
            escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre());
        }

        escpos.writeLF("Fecha: " + venta.getCreadoEn().format(formatter));
        escpos.writeLF("--------------------------------");

        if (venta.getCliente() != null) {
            escpos.writeLF("Cliente: " + venta.getCliente().getPersona().getNombre().substring(0, 22));
        }
        escpos.writeLF("Producto");
        escpos.writeLF("Cant    P.U                 P.T");
        escpos.writeLF("--------------------------------");
        for (VentaItem vi : ventaItemList) {
            String cantidad = vi.getCantidad().intValue() + " (" + vi.getPresentacion().getCantidad() + ")";
//            log.info(vi.getProducto().getDescripcion());
            escpos.writeLF(vi.getProducto().getDescripcion());
            escpos.write(new Style().setBold(true), cantidad);
            String valorUnitario = NumberFormat.getNumberInstance(Locale.GERMAN).format(vi.getPrecioVenta().getPrecio().intValue());
            String valorTotal = String.valueOf(vi.getPrecioVenta().getPrecio().intValue() * vi.getCantidad().intValue());
            for (int i = 10; i > cantidad.length(); i--) {
                escpos.write(" ");
            }
            escpos.write(valorUnitario);
            for (int i = 20 - valorUnitario.length(); i > valorTotal.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(NumberFormat.getNumberInstance(Locale.GERMAN).format(vi.getPrecioVenta().getPrecio().intValue() * vi.getCantidad().intValue()));
        }
        escpos.writeLF("--------------------------------");
        String valorGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(venta.getTotalGs().intValue());
        for (int i = 22; i > valorGs.length(); i--) {
            escpos.write(" ");
        }
        escpos.writeLF(valorGs);
//        log.info(valorGs);
        escpos.write("Total Rs: ");
        String valorRs = String.format("%.2f", venta.getTotalRs());
        for (int i = 22; i > valorGs.length(); i--) {
            escpos.write(" ");
        }
        escpos.writeLF(valorRs);
        escpos.write("Total Ds: ");
//      String valorDs = NumberFormat.getNumberInstance(new Locale("sk", "SK")).format(venta.getTotalDs());
        String valorDs = String.format("%.2f", venta.getTotalDs());
        for (int i = 22; i > valorGs.length(); i--) {
            escpos.write(" ");
        }
        escpos.writeLF(valorDs);
        if (sucursal != null && sucursal.getNroDelivery() != null) {
            escpos.write(center, "Delivery? Escaneá el código qr o escribinos al ");
            escpos.writeLF(center, sucursal.getNroDelivery());
        }
//        escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center), "wa.me/595986128000");
        escpos.feed(1);
        escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
        escpos.feed(5);

        try {
            escpos.close();
            printerOutputStream.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public Boolean imprimirFacturasPorCaja(Long id, String printerName, Long sucId) {
        Boolean ok = false;
        List<FacturaLegal> facturaLegalList = service.findByCajaId(id);
        Integer count = 0;
        for (FacturaLegal fl : facturaLegalList) {
            count++;
            List<FacturaLegalItem> facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(fl.getId());
            List<FacturaLegalItemInput> facturaLegalItemInputList = new ArrayList<>();
            ModelMapper m = new ModelMapper();
            FacturaLegalInput input = m.map(fl, FacturaLegalInput.class);
            input.setClienteId(fl.getCliente().getId());
            if (fl.getCaja() != null) input.setCajaId(fl.getCaja().getId());
            if (fl.getVenta() != null) input.setVentaId(fl.getVenta().getId());
            input.setUsuarioId(fl.getUsuario().getId());
            input.setTimbradoDetalleId(fl.getTimbradoDetalle().getId());
            for (FacturaLegalItem flItem : facturaLegalItemList) {
                ModelMapper i = new ModelMapper();
                FacturaLegalItemInput itemInput = i.map(flItem, FacturaLegalItemInput.class);
                if (flItem.getUsuario() != null) itemInput.setUsuarioId(flItem.getUsuario().getId());
                if (flItem.getVentaItem() != null) itemInput.setVentaItemId(flItem.getVentaItem().getId());
                if (flItem.getFacturaLegal() != null) itemInput.setFacturaLegalId(flItem.getFacturaLegal().getId());
                facturaLegalItemInputList.add(itemInput);
            }
            Boolean continuar = true;
            if (facturaLegalList.size() == count) {
                continuar = false;
            }
            SaveFacturaDto dto = generarFacturaAutoImpreso(fl.getVenta() != null ? fl.getVenta() : null, input, facturaLegalItemInputList, printerName, fl.getTimbradoDetalle().getPuntoDeVenta().getId(), continuar, true);
            if (dto != null) {
                fl.setViaTributaria(true);
                propagacionService.propagarEntidad(fl, TipoEntidad.FACTURA);
                ok = true;
            }
        }
        return ok;
    }

    public Boolean reimprimirFacturaLegal(Long id, Long sucId, String printerName){
        FacturaLegal facturaLegal = service.findById(id).orElse(null);
        List<FacturaLegalItem> facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(id);
        try {
            printTicket58mmFactura(facturaLegal.getVenta(), facturaLegal, facturaLegalItemList, printerName);
            return true;
        } catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    public void printTicket58mmFactura(Venta venta, FacturaLegal facturaLegal, List<FacturaLegalItem> facturaLegalItemList, String printerName) throws Exception {
        SaveFacturaDto saveFacturaDto = new SaveFacturaDto();
        printService = PrinterOutputStream.getPrintServiceByName(printerName);
        Sucursal sucursal = sucursalService.findById(facturaLegal.getSucursalId()).orElse(null);
        Delivery delivery = null;
        if(venta != null ) delivery = deliveryService.findByVentaId(venta.getId(), venta.getSucursalId());
        Double descuento = 0.0;
        Double aumento = 0.0;
        Double vueltoGs = 0.0;
        Double vueltoRs = 0.0;
        Double vueltoDs = 0.0;
        Double pagadoGs = 0.0;
        Double pagadoRs = 0.0;
        Double pagadoDs = 0.0;
        Double ventaIva10 = 0.0;
        Double ventaIva5 = 0.0;
        Double ventaIva0 = 0.0;
        Double totalIva10 = 0.0;
        Double totalIva5 = 0.0;
        Double totalIva = 0.0;
        Double totalFinal = 0.0;
        Double precioDeliveryGs = 0.0;
        Double precioDeliveryRs = 0.0;
        Double precioDeliveryDs = 0.0;
        Double cambioRs = cambioService.findLastByMonedaId(Long.valueOf(2)).getValorEnGs();
        Double cambioDs = cambioService.findLastByMonedaId(Long.valueOf(3)).getValorEnGs();

        if(delivery!=null){
            precioDeliveryGs = delivery.getPrecio().getValor();
            precioDeliveryRs = precioDeliveryGs / cambioRs;
            precioDeliveryDs = precioDeliveryGs / cambioDs;
        }

        if (printService != null) {
            printerOutputStream = this.printerOutputStream != null ? this.printerOutputStream : new PrinterOutputStream(printService);
            // creating the EscPosImage, need buffered image and algorithm.
            //Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center).setFontSize(Style.FontSize._1, Style.FontSize._1);
            QRCode qrCode = new QRCode();

            BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
            imageBufferedImage = resize(imageBufferedImage, 200, 100);
            BitImageWrapper imageWrapper = new BitImageWrapper();
            EscPos escpos = new EscPos(printerOutputStream);
            Bitonal algorithm = new BitonalThreshold();
            EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
            imageWrapper.setJustification(EscPosConst.Justification.Center);
            escpos.writeLF("--------------------------------");
            escpos.write(imageWrapper, escposImage);
            escpos.writeLF(factura, facturaLegal.getTimbradoDetalle().getTimbrado().getRazonSocial().toUpperCase());
            escpos.writeLF(factura, "RUC: " + facturaLegal.getTimbradoDetalle().getTimbrado().getRuc());
            escpos.writeLF(factura, "Timbrado: " + facturaLegal.getTimbradoDetalle().getTimbrado().getNumero());
            escpos.writeLF(factura, "De " + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaInicio().format(impresionService.shortDate) + " a " + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaFin().format(impresionService.shortDate));
            Long numeroFacturaAux = Long.valueOf(facturaLegal.getNumeroFactura());
            StringBuilder numeroFacturaString = new StringBuilder();
            for (int i = 7; i > numeroFacturaAux.toString().length(); i--) {
                numeroFacturaString.append("0");
            }
            if (facturaLegal.getNumeroFactura() != null) {
                numeroFacturaString.append(facturaLegal.getNumeroFactura());
            } else {
                numeroFacturaString.append(numeroFacturaAux.toString());
            }
            escpos.writeLF(factura, "Nro: " + sucursal.getCodigoEstablecimientoFactura() + "-" + facturaLegal.getTimbradoDetalle().getPuntoExpedicion() + "-" + numeroFacturaString.toString());
            escpos.writeLF(center, "Condición: " + (facturaLegal.getCredito() == false ? "Contado" : "Crédito"));

            if (sucursal != null) {
                escpos.writeLF(center, "Suc: " + sucursal.getNombre());
                if (sucursal.getCiudad() != null) {
                    escpos.writeLF(center, sucursal.getCiudad().getDescripcion());
                    if (sucursal.getDireccion() != null) {
                        escpos.writeLF(center, sucursal.getNombre() + " - " + sucursal.getDireccion());
                    }
                }
            }
            if (venta != null) escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());
            if(delivery!=null){
                escpos.writeLF(center, "Modo: Delivery");
            }
            if (venta != null && venta.getUsuario() != null) {
                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre());
            }

            escpos.writeLF("Fecha: " + facturaLegal.getCreadoEn().format(shortDateTime));
            escpos.writeLF("--------------------------------");

            String nombreCliente = facturaLegal.getNombre().toUpperCase();
            nombreCliente = nombreCliente.replace("Ñ", "N")
                    .replace("Á", "A")
                    .replace("É", "E")
                    .replace("Í", "I")
                    .replace("Ó", "O")
                    .replace("Ú", "U");
            escpos.writeLF("Cliente: " + nombreCliente);

            if(facturaLegal.getRuc()!=null){
                if(!facturaLegal.getRuc().contains("-")){
                    facturaLegal.setRuc(facturaLegal.getRuc()+getDigitoVerificadorString(facturaLegal.getRuc()));
                };
            }

            escpos.writeLF("CI/RUC: " + facturaLegal.getRuc());
            if (facturaLegal.getDireccion() != null)
                escpos.writeLF("Dir: " + facturaLegal.getDireccion());

            escpos.writeLF("--------------------------------");

            escpos.writeLF("Producto");
            escpos.writeLF("Cant  IVA   P.U              P.T");
            escpos.writeLF("--------------------------------");
            for (FacturaLegalItem vi : facturaLegalItemList) {
                Integer iva = null;
                if(vi.getPresentacion() != null){
                    iva = vi.getPresentacion().getProducto().getIva();
                }
                Double total = vi.getTotal();
                if (iva == null) {
                    iva = 10;
                }
                switch (iva) {
                    case 10:
                        ventaIva10 += total;
                        totalIva10 += total / 11;
                        break;
                    case 5:
                        totalIva5 += total / 21;
                        ventaIva5 += total;
                        break;
                    case 0:
                        ventaIva0 += total;
                        break;

                }
                totalFinal += total;
                String cantidad = vi.getCantidad().intValue() + " (" + vi.getCantidad() + ") " + iva + "%";
                escpos.writeLF(vi.getDescripcion());
                escpos.write(new Style().setBold(true), cantidad);
                String valorUnitario = NumberFormat.getNumberInstance(Locale.GERMAN).format(vi.getPrecioUnitario().intValue());
                String valorTotal = NumberFormat.getNumberInstance(Locale.GERMAN).format(total.intValue());
                for (int i = 14; i > cantidad.length(); i--) {
                    escpos.write(" ");
                }
                escpos.write(valorUnitario);
                for (int i = 16 - valorUnitario.length(); i > valorTotal.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTotal);
            }
            escpos.writeLF("--------------------------------");
            escpos.write("Total Gs: ");
            String valorGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal);
            for (int i = 22; i > valorGs.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(new Style().setBold(true), valorGs);
            escpos.writeLF("--------Liquidación IVA---------");
            escpos.write("Gravadas 10%:");
            String totalIva10S = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva10.intValue());
            for (int i = 19; i > totalIva10S.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva10S);
            escpos.write("Gravadas 5%: ");
            String totalIva5S = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva5.intValue());
            for (int i = 19; i > totalIva5S.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva5S);
            escpos.write("Exentas:     ");
            for (int i = 19; i > 1; i--) {
                escpos.write(" ");
            }
            escpos.writeLF("0");
            Double totalFinalIva = totalIva10 + totalIva5;
            String totalFinalIvaS = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinalIva.intValue());
            escpos.write("Total IVA:   ");
            for (int i = 19; i > totalFinalIvaS.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalFinalIvaS);
//            escpos.writeLF("--------Liquidación IVA---------");
//            escpos.write("Gravadas 10%:");
//            Double totalIvaFinal = totalIva10 + totalIva5;
//            String totalIvaFinalS = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIvaFinal.intValue());
//            for (int i = 19; i > totalIvaFinalS.length(); i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF(iva10s);
//            escpos.write("Gravadas 5%: ");
//            for (int i = 19; i > 1; i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF("0");

            escpos.writeLF("--------------------------------");
            if (sucursal != null && sucursal.getNroDelivery() != null) {
                escpos.write(center, "Delivery? Escaneá el código qr o escribinos al ");
                escpos.writeLF(center, sucursal.getNroDelivery());
            }
            if (sucursal.getNroDelivery() != null) {
                escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center), "wa.me/" + sucursal.getNroDelivery());
            }
            escpos.feed(1);
            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
//            escpos.writeLF("--------------------------------");
//            escpos.write( "Conservar este papel ");
            escpos.feed(5);

            try {
                if (true) {
                    escpos.close();
                    printerOutputStream.close();
                    this.printerOutputStream = null;
                } else {
                    this.printerOutputStream = printerOutputStream;
                }
//                if (facturaLegal.getId() == null) {
//                    Long numero = timbradoDetalleService.aumentarNumeroFactura(timbradoDetalle);
//                    facturaLegal.setTimbradoDetalleId(timbradoDetalle.getId());
//                    if(venta!=null){
//                        facturaLegal.setVentaId(venta.getId());
//                        facturaLegal.setFecha(venta.getCreadoEn());
//                        facturaLegal.setClienteId(venta.getCliente().getId());
//                        facturaLegal.setCajaId(venta.getCaja().getId());
//                    }
//                    facturaLegal.setTotalFinal(totalFinal);
//                    facturaLegal.setIvaParcial5(totalIva5);
//                    facturaLegal.setIvaParcial10(totalIva10);
//                    facturaLegal.setViaTributaria(false);
//                    facturaLegal.setAutoimpreso(true);
//                    facturaLegal.setNumeroFactura(numero.intValue());
//                    facturaLegal.setTotalParcial5(ventaIva5);
//                    facturaLegal.setTotalParcial10(ventaIva10);
//                    facturaLegal.setTotalParcial0(ventaIva0);
//                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }


}
