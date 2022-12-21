package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.PuntoDeVenta;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.rabbit.dto.SaveFacturaDto;
import com.franco.dev.service.empresarial.PuntoDeVentaService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.barcode.QRCode;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.GraphQLException;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.franco.dev.service.impresion.ImpresionService.shortDateTime;
import static com.franco.dev.service.utils.PrintingService.resize;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;

@Service
public class FacturaService {

    private PrintService printService;
    private PrinterOutputStream printerOutputStream;
    private EscPos escPos;
    @Autowired
    private ImageService imageService;
    @Autowired
    private SucursalService sucursalService;
    @Autowired
    private PrintingService printingService;
    @Autowired
    private PuntoDeVentaService puntoDeVentaService;
    @Autowired
    private TimbradoDetalleService timbradoDetalleService;
    @Autowired
    private ImpresionService impresionService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private CambioService cambioService;

    public Long printTicket58mmFacturaConVenta(Venta venta, Cobro cobro, List<VentaItem> ventaItemList, List<CobroDetalleInput> cobroDetalleList, Boolean reimpresion, String printerName, String local, FacturaLegalInput facturaLegal, Long pdvId, Long numeroFactura, Delivery delivery) throws Exception {
        PrintService selectedPrintService = printingService.getPrintService(printerName);
        Sucursal sucursal = sucursalService.sucursalActual();
        PuntoDeVenta puntoDeVenta = puntoDeVentaService.getPuntoDeVentaActual(pdvId);
        TimbradoDetalle timbradoDetalle = timbradoDetalleService.getTimbradoDetalleActual(puntoDeVenta.getId());

        Double totalIva10 = 0.0;
        Double totalIva5 = 0.0;
        Double totalIva = 0.0;
        Double totalFinal = 0.0;

        if (selectedPrintService != null) {
            printerOutputStream = new PrinterOutputStream(selectedPrintService);
            // creating the EscPosImage, need buffered image and algorithm.
            //Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center).setFontSize(Style.FontSize._1, Style.FontSize._1);
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
            escpos.writeLF(factura, timbradoDetalle.getTimbrado().getRazonSocial());
            escpos.writeLF(factura, "RUC: " + timbradoDetalle.getTimbrado().getRuc());
            escpos.writeLF(factura, "Timbrado: " + timbradoDetalle.getTimbrado().getNumero());
            escpos.writeLF(factura, "De " + timbradoDetalle.getTimbrado().getFechaInicio().format(impresionService.shortDate) + " a " + timbradoDetalle.getTimbrado().getFechaFin().format(impresionService.shortDate));
            Long numeroFacturaAux = timbradoDetalle.getNumeroActual() + 1;
            if (numeroFactura != null) {
                numeroFacturaAux = numeroFactura;
            }
            StringBuilder numeroFacturaString = new StringBuilder();
            for (int i = 7; i > numeroFacturaAux.toString().length(); i--) {
                numeroFacturaString.append("0");
            }
            numeroFacturaString.append(numeroFacturaAux.toString());
            escpos.writeLF(factura, "Nro: " + sucursalService.sucursalActual().getCodigoEstablecimientoFactura() + "-" + timbradoDetalle.getPuntoExpedicion() + "-" + numeroFacturaString.toString());
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
            escpos.writeLF(center, "Local: " + puntoDeVenta.getNombre());
            escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());

            if (venta.getUsuario().getPersona().getNombre().length() > 23) {
                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre().substring(0, 23));

            } else {
                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre());
            }

            escpos.writeLF("Fecha: " + venta.getCreadoEn().format(shortDateTime));
            escpos.writeLF("--------------------------------");

            if (venta.getCliente() != null) {
                escpos.writeLF("Cliente: " + venta.getCliente().getPersona().getNombre());
                escpos.writeLF("CI/RUC: " + venta.getCliente().getPersona().getDocumento());
                if (venta.getCliente().getPersona().getDireccion() != null)
                    escpos.writeLF("Dir: " + venta.getCliente().getPersona().getDireccion());
            }

            escpos.writeLF("--------------------------------");

            escpos.writeLF("Producto");
            escpos.writeLF("Cant  IVA   P.U              P.T");
            escpos.writeLF("--------------------------------");
            for (VentaItem vi : ventaItemList) {
                Integer iva = vi.getPresentacion().getProducto().getIva();
                Double total = vi.getPrecioVenta().getPrecio().intValue() * vi.getCantidad();
                if (iva == null) {
                    iva = 10;
                }
                switch (iva) {
                    case 10:
                        totalIva10 += total / 11;
                        break;
                    case 5:
                        totalIva5 += total / 21;
                        break;

                }
                String cantidad = vi.getCantidad().intValue() + " (" + vi.getPresentacion().getCantidad().intValue() + ") " + iva + "%";
                escpos.writeLF(vi.getProducto().getDescripcion());
                escpos.write(new Style().setBold(true), cantidad);
                String valorUnitario = NumberFormat.getNumberInstance(Locale.GERMAN).format(vi.getPrecioVenta().getPrecio().intValue());
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
            String valorGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(venta.getTotalGs().intValue());
            for (int i = 22; i > valorGs.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(new Style().setBold(true).setFontSize(Style.FontSize._0, Style.FontSize._0), valorGs);
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
                escpos.close();
                printerOutputStream.close();
                if (numeroFactura == null) {
                    return timbradoDetalleService.aumentarNumeroFactura(timbradoDetalle);
                } else {
                    return numeroFactura;
                }

            } catch (IOException ioe) {
                ioe.printStackTrace();
                return null;
            }
        }
        return null;
    }

    public SaveFacturaDto printTicket58mmFactura(Venta venta, FacturaLegalInput facturaLegal, List<FacturaLegalItemInput> facturaLegalItemList, String printerName, Long pdvId, Boolean continuar, Delivery delivery) throws Exception {
        SaveFacturaDto saveFacturaDto = new SaveFacturaDto();
        PrintService selectedPrintService = printingService.getPrintService(printerName);
        Sucursal sucursal = sucursalService.sucursalActual();
        PuntoDeVenta puntoDeVenta = puntoDeVentaService.getPuntoDeVentaActual(pdvId);
        TimbradoDetalle timbradoDetalle = timbradoDetalleService.getTimbradoDetalleActual(puntoDeVenta.getId());
        Usuario cajero = venta != null ? venta.getUsuario() : usuarioService.findById(facturaLegal.getUsuarioId()).orElse(null);
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

        if (selectedPrintService != null) {
            printerOutputStream = this.printerOutputStream != null ? this.printerOutputStream : new PrinterOutputStream(selectedPrintService);
            // creating the EscPosImage, need buffered image and algorithm.
            //Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center).setFontSize(Style.FontSize._1, Style.FontSize._1);
            QRCode qrCode = new QRCode();

            BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
            imageBufferedImage = resize(imageBufferedImage, 200, 100);
            RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
            EscPos escpos = this.escPos != null ? this.escPos : null;
            escpos = new EscPos(printerOutputStream);
            Bitonal algorithm = new BitonalThreshold();
            EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
            imageWrapper.setJustification(EscPosConst.Justification.Center);
            escpos.write(imageWrapper, escposImage);
            escpos.writeLF(factura, timbradoDetalle.getTimbrado().getRazonSocial().toUpperCase());
            escpos.writeLF(factura, "RUC: " + timbradoDetalle.getTimbrado().getRuc());
            escpos.writeLF(factura, "Timbrado: " + timbradoDetalle.getTimbrado().getNumero());
            escpos.writeLF(factura, "De " + timbradoDetalle.getTimbrado().getFechaInicio().format(impresionService.shortDate) + " a " + timbradoDetalle.getTimbrado().getFechaFin().format(impresionService.shortDate));
            Long numeroFacturaAux = timbradoDetalle.getNumeroActual() + 1;
            StringBuilder numeroFacturaString = new StringBuilder();
            for (int i = 7; i > numeroFacturaAux.toString().length(); i--) {
                numeroFacturaString.append("0");
            }
            if (facturaLegal.getNumeroFactura() != null) {
                numeroFacturaString.append(facturaLegal.getNumeroFactura());
            } else {
                numeroFacturaString.append(numeroFacturaAux.toString());
            }
            escpos.writeLF(factura, "Nro: " + sucursalService.sucursalActual().getCodigoEstablecimientoFactura() + "-" + timbradoDetalle.getPuntoExpedicion() + "-" + numeroFacturaString.toString());
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
            escpos.writeLF(center, "Local: " + puntoDeVenta.getNombre());
            if (venta != null) escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());
            if(delivery!=null){
                escpos.writeLF(center, "Modo: Delivery");
            }
            if (cajero != null) {
                escpos.writeLF("Cajero: " + cajero.getPersona().getNombre());
            }

            escpos.writeLF("Fecha: " + LocalDateTime.now().format(shortDateTime));
            escpos.writeLF("--------------------------------");

            escpos.writeLF("Cliente: " + facturaLegal.getNombre().toUpperCase());
            escpos.writeLF("CI/RUC: " + facturaLegal.getRuc());
            if (facturaLegal.getDireccion() != null)
                escpos.writeLF("Dir: " + facturaLegal.getDireccion());

            escpos.writeLF("--------------------------------");

            escpos.writeLF("Producto");
            escpos.writeLF("Cant  IVA   P.U              P.T");
            escpos.writeLF("--------------------------------");
            for (FacturaLegalItemInput vi : facturaLegalItemList) {
                Integer iva = vi.getIva();
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
                if (continuar != true) {
                    escpos.close();
                    printerOutputStream.close();
                    this.escPos = null;
                    this.printerOutputStream = null;
                } else {
                    this.escPos = escpos;
                    this.printerOutputStream = printerOutputStream;
                }
                if (facturaLegal.getId() == null) {
                    Long numero = timbradoDetalleService.aumentarNumeroFactura(timbradoDetalle);
                    facturaLegal.setTimbradoDetalleId(timbradoDetalle.getId());
                    if(venta!=null){
                        facturaLegal.setVentaId(venta.getId());
                        facturaLegal.setFecha(venta.getCreadoEn());
                        facturaLegal.setClienteId(venta.getCliente().getId());
                        facturaLegal.setCajaId(venta.getCaja().getId());
                    }
                    facturaLegal.setTotalFinal(totalFinal);
                    facturaLegal.setIvaParcial5(totalIva5);
                    facturaLegal.setIvaParcial10(totalIva10);
                    facturaLegal.setViaTributaria(false);
                    facturaLegal.setAutoimpreso(true);
                    facturaLegal.setNumeroFactura(numero.intValue());
                    facturaLegal.setTotalParcial5(ventaIva5);
                    facturaLegal.setTotalParcial10(ventaIva10);
                    facturaLegal.setTotalParcial0(ventaIva0);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        saveFacturaDto.setFacturaLegalInput(facturaLegal);
        saveFacturaDto.setFacturaLegalItemInputList(facturaLegalItemList);
        return saveFacturaDto;
    }

//    public void generarFactura() {
//        try {
//            FacturaDto facturaDto = new FacturaDto();
//            facturaDto.setContado("X");
//            facturaDto.setFecha("10/12/2022");
//            facturaDto.setIvaParcial("33.500");
//            facturaDto.setNombre("Gabriel Francisco Franco Arevalos");
//            facturaDto.setRuc("4043581-4");
//            facturaDto.setTotal("350.000");
//            facturaDto.setTotalEnLetras("Trescientos cincuenta mil");
//            facturaDto.setDireccion("Av. Paraguay c/ 30 de julio");
//            List<VentaItemDto> ventaItemList = new ArrayList<>();
//            ventaItemList.add(new VentaItemDto("5", "Brahma lata 269", "3.500", "120.000"));
//            ventaItemList.add(new VentaItemDto("2", "Skol lata", "3.500", "7.000"));
//            ventaItemList.add(new VentaItemDto("7", "Producto cualquiera", "8.500", "50.000"));
//
//            File file = ResourceUtils.getFile("classpath:factura.jrxml");
//            JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
//            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(ventaItemList);
//            Map<String, Object> parameters = new HashMap<>();
//            parameters.put("contado", facturaDto.getContado());
//            parameters.put("credito", facturaDto.getCredito());
//            parameters.put("fecha", facturaDto.getFecha());
//            parameters.put("ivaTotal", facturaDto.getIvaParcial());
//            parameters.put("nombre", facturaDto.getNombre());
//            parameters.put("ruc", facturaDto.getRuc());
//            parameters.put("totalFinal", facturaDto.getTotal());
//            parameters.put("totalEnLetras", facturaDto.getTotalEnLetras());
//            parameters.put("direccion", facturaDto.getDireccion());
//
//            JasperPrint jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
//            jasperPrint1.setPageHeight(842);
//
//            List<VentaItemDto> ventaItemList2 = new ArrayList<>();
//            ventaItemList.add(new VentaItemDto("5", "Brahma lata 269", "3.500", "120.000"));
//            ventaItemList.add(new VentaItemDto("2", "Skol lata", "3.500", "7.000"));
//            ventaItemList.add(new VentaItemDto("7", "Producto cualquiera", "8.500", "50.000"));
//
//            File file2 = ResourceUtils.getFile("classpath:factura2.jrxml");
//            JasperReport jasperReport2 = JasperCompileManager.compileReport(file.getAbsolutePath());
//            JRBeanCollectionDataSource dataSource2 = new JRBeanCollectionDataSource(ventaItemList);
//            Map<String, Object> parameters2 = new HashMap<>();
//            parameters2.put("contado", facturaDto.getContado());
//            parameters2.put("credito", facturaDto.getCredito());
//            parameters2.put("fecha", facturaDto.getFecha());
//            parameters2.put("ivaTotal", facturaDto.getIvaParcial());
//            parameters2.put("nombre", facturaDto.getNombre());
//            parameters2.put("ruc", facturaDto.getRuc());
//            parameters2.put("totalFinal", facturaDto.getTotal());
//            parameters2.put("totalEnLetras", facturaDto.getTotalEnLetras());
//            parameters2.put("direccion", facturaDto.getDireccion());
//
//            JasperPrint jasperPrint2 = JasperFillManager.fillReport(jasperReport2, parameters2, dataSource2);
//
//            JRPrintPage page2 = jasperPrint2.getPages().get(0);
//            List<JRPrintElement> elements = page2.getElements();
//
//            for(JRPrintElement e: elements){
//                e.setY(e.getY() + 421);
//                jasperPrint1.getPages().get(0).addElement(e);
//            }
//
//            OutputStream output;
//            output = new FileOutputStream(new File("/Users/gabfranck/Desktop/prueba.pdf"));
//            JasperExportManager.exportReportToPdfStream(jasperPrint1, output);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    public void printFactura(JasperPrint jasperPrint) throws GraphQLException {
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

        printService = PrinterOutputStream.getPrintServiceByName("FACTURA");


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
}
