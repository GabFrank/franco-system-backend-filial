package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.dto.VentaPorPeriodoV1Dto;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
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
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.franco.dev.service.utils.PrintingService.resize;

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

    private Sucursal sucursal;

    public Optional<Venta> venta(Long id) {
        return service.findById(id);
    }

    public List<Venta> ventas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

//    public List<Venta> ventaSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean saveVenta(VentaInput ventaInput, List<VentaItemInput> ventaItemList, CobroInput cobroInput, List<CobroDetalleInput> cobroDetalleList, Boolean ticket, String printerName, String local) throws Exception {
        Boolean ok = false;
        Venta venta = null;
        Cobro cobro = cobroGraphQL.saveCobro(cobroInput, cobroDetalleList, ventaInput.getCajaId());
        List<VentaItem> ventaItemList1 = new ArrayList<>();
        if (cobro != null) {
            ModelMapper m = new ModelMapper();
            Venta e = m.map(ventaInput, Venta.class);
            if (e.getUsuario() != null) e.setUsuario(usuarioService.findById(ventaInput.getUsuarioId()).orElse(null));
            if (e.getCliente() != null) e.setCliente(clienteService.findById(ventaInput.getClienteId()).orElse(null));
            if (e.getFormaPago() != null)
                e.setFormaPago(formaPagoService.findById(ventaInput.getFormaPagoId()).orElse(null));
            if (e.getCaja() != null) e.setCaja(pdvCajaService.findById(ventaInput.getCajaId()).orElse(null));
            e.setCobro(cobro);
            venta = service.save(e);
            if (venta != null) {
                ventaItemList1 = ventaItemGraphQL.saveVentaItemList(ventaItemList, venta.getId());
            }
            ok = venta.getId() != null;
        }
        if (ok == false) {
            deshacerVenta(venta, cobro);
        } else {
            try {
                if (ticket) printTicket58mm(venta, cobro, ventaItemList1, cobroDetalleList, false, printerName, local);
            } catch (Exception e) {
                return ok;
            }
        }
        return ok;
    }

    public Boolean deleteVenta(Long id) {
        return service.deleteById(id);
    }

    public Long countVenta() {
        return service.count();
    }

    public void deshacerVenta(Venta venta, Cobro cobro) {
        if (cobro != null) {
            cobroGraphQL.deleteCobro(cobro.getId());
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


    public void printTicket58mm(Venta venta, Cobro cobro, List<VentaItem> ventaItemList, List<CobroDetalleInput> cobroDetalleList, Boolean reimpresion, String printerName, String local) throws Exception {
        PrintService selectedPrintService = null;
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        System.out.println("Number of print services: " + printServices.length);
        for (PrintService printer : printServices) {
            System.out.println("Printer: " + printer.getName());
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

        if(selectedPrintService!=null) printerOutputStream = new PrinterOutputStream(selectedPrintService);

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
            log.info(vi.getProducto().getDescripcion());
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
        log.info(valorGs);
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

    public List<Venta> ventasPorCajaId(Long id, Integer offset) {
        return service.findByCajaId(id, offset);
    }

    public Boolean cancelarVenta(Long id) {
        Venta venta = service.findById(id).orElse(null);
        if (venta != null && venta.getEstado() != VentaEstado.CANCELADA) {
            venta.setEstado(VentaEstado.CANCELADA);
            venta = service.save(venta);
            if (venta != null) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    public Boolean reimprimirVenta(Long id, String printerName, String local) throws Exception {
        Venta venta = service.findById(id).orElse(null);
        if (venta != null) {
            Cobro cobro = cobroGraphQL.cobro(venta.getCobro().getId()).orElse(null);
            List<VentaItem> ventaItemList = ventaItemGraphQL.ventaItemListPorVentaId(venta.getId());
            if (cobro != null) {
                List<CobroDetalleInput> cobroDetalleList = new ArrayList<>();
                printTicket58mm(venta, cobro, ventaItemList, cobroDetalleList, true, printerName, local);
                return true;
            }
        }
        return false;
    }

    public List<VentaPorPeriodoV1Dto> ventaPorPeriodo(String inicio, String fin) {
        return service.ventaPorPeriodo(inicio, fin);
    }
}
