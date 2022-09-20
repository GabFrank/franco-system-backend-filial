package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.empresarial.PuntoDeVenta;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.operaciones.Cobro;
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

import static com.franco.dev.service.utils.PrintingService.resize;
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

    public Optional<FacturaLegal> facturaLegal(Long id) {
        return service.findById(id);
    }

    public List<FacturaLegal> facturaLegales(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Transactional
    public Boolean saveFacturaLegal(FacturaLegalInput input, List<FacturaLegalItemInput> facturaLegalItemInputList, String printerName, Long pdvId) {
        Venta venta = input.getVentaId() != null ? ventaService.findById(input.getVentaId()).orElse(null) : null;
        SaveFacturaDto saveFacturaDto = generarFacturaAutoImpreso(venta, input, facturaLegalItemInputList, printerName, pdvId, false);
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

    public Boolean deleteFacturaLegal(Long id) {
        return service.deleteById(id);
    }

    public Long countFacturaLegal() {
        return service.count();
    }

    public SaveFacturaDto generarFacturaAutoImpreso(Venta venta, FacturaLegalInput facturaLegal, List<FacturaLegalItemInput> facturaLegalItemList, String printerName, Long pdvId, Boolean continuar) {
        PuntoDeVenta puntoDeVenta = puntoDeVentaService.getPuntoDeVentaActual(pdvId);
        TimbradoDetalle timbradoDetalle = puntoDeVenta != null ? timbradoDetalleService.getTimbradoDetalleActual(puntoDeVenta.getId()) : null;
        if (timbradoDetalle != null) {
            try {
                return facturaService.printTicket58mmFactura(venta, facturaLegal, facturaLegalItemList, printerName, pdvId, continuar);
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
        RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
        EscPos escpos = null;
        escpos = new EscPos(printerOutputStream);
        Bitonal algorithm = new BitonalThreshold();
        EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
        imageWrapper.setJustification(EscPosConst.Justification.Center);
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

    public Boolean imprimirFacturasPorCaja(Long id, String printerName) {
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
            SaveFacturaDto dto = generarFacturaAutoImpreso(fl.getVenta() != null ? fl.getVenta() : null, input, facturaLegalItemInputList, printerName, fl.getTimbradoDetalle().getPuntoDeVenta().getId(), continuar);
            if (dto != null) {
                fl.setViaTributaria(true);
                propagacionService.propagarEntidad(fl, TipoEntidad.FACTURA);
                ok = true;
            }
        }
        return ok;
    }


}
