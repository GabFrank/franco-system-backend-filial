package com.franco.dev.service.impresion;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaDto;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.impresion.dto.GastoDto;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.barcode.QRCode;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.franco.dev.service.utils.PrintingService.resize;

@Service
public class ImpresionService {

    PrintService selectedPrintService = null;

    @Autowired
    private ImageService imageService;

    @Autowired
    private PrintingService printingService;

    private PrinterOutputStream printerOutputStream;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private TimbradoDetalleService timbradoDetalleService;

    private Sucursal sucursal = null;

    public static DateTimeFormatter shortDate = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    public static DateTimeFormatter shortDateTime = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    public Boolean printBalance(PdvCajaBalanceDto balanceDto, String printerName, String local) {
        try {
            if(printerName==null){
                selectedPrintService = printingService.getLasUsedPrinter();
            } else {
                selectedPrintService = printingService.getPrintService(printerName);
            }
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
                // creating the EscPosImage, need buffered image and algorithm.
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
                //Styles
                Style center = new Style().setJustification(EscPosConst.Justification.Center);

                QRCode qrCode = new QRCode();

                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
                imageBufferedImage = resize(imageBufferedImage, 200, 100);
                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
                EscPos escpos = new EscPos(printerOutputStream);
                Bitonal algorithm = new BitonalThreshold();
                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                imageWrapper.setJustification(EscPosConst.Justification.Center);
                escpos.write(imageWrapper, escposImage);
//                escpos.writeLF(center.setBold(true), "SUC. CENTRO");
//                escpos.writeLF(center, "Salto del Guairá");
                if (balanceDto.getSucursal() != null) {
                    escpos.writeLF(center, "Suc: " + balanceDto.getSucursal().getNombre());
                }
                if (local != null) {
                    escpos.writeLF(center, "Local: " + local);
                }
                escpos.writeLF(center.setBold(true), "Caja: " + balanceDto.getIdCaja());
                if (balanceDto.getUsuario().getPersona().getNombre().length() > 23) {
                    escpos.writeLF("Cajero: " + balanceDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + balanceDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha Apertura: " + balanceDto.getFechaApertura().format(formatter));
                escpos.writeLF("Fecha Cierre: " + balanceDto.getFechaCierre().format(formatter));
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE APERTURA");
                escpos.write("Guaranies G$: ");
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalGsAper().intValue());
                for (int i = 18; i > valorGsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsAper);
                escpos.write("Reales R$: ");
                String valorRsAper = String.format("%.2f", balanceDto.getTotalRsAper());
                for (int i = 21; i > valorRsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsAper);
                escpos.write("Dolares D$: ");
                String valorDsAper = String.format("%.2f", balanceDto.getTotalDsAper());
                for (int i = 20; i > valorDsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsAper);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE CIERRE");
                escpos.write("Guaranies G$: ");
                String valorGsCierre = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalGsCierre().intValue());
                for (int i = 18; i > valorGsCierre.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsCierre);
                escpos.write("Reales R$: ");
                String valorRsCierre = String.format("%.2f", balanceDto.getTotalRsCierre());
                for (int i = 21; i > valorRsCierre.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsCierre);
                escpos.write("Dolares D$: ");
                String valorDsCierre = String.format("%.2f", balanceDto.getTotalDsCierre());
                for (int i = 20; i > valorDsCierre.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsCierre);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE TARJETA");
                escpos.write("Guaranies G$: ");
                String valorTarjeta = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalTarjeta().intValue());
                for (int i = 18; i > valorTarjeta.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTarjeta);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE CREDITO");
                escpos.write("Guaranies G$: ");
                String valorCredito = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalCredito().intValue());
                for (int i = 18; i > valorCredito.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorCredito);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE RETIRO");
                String valorGsRetiro = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalRetiroGs().intValue());
                escpos.write("Guaranies G$: ");
                for (int i = 18; i > valorGsRetiro.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsRetiro);
                String valorRsRetiro = String.format("%.2f", balanceDto.getTotalRetiroRs());
                escpos.write("Reales R$: ");
                for (int i = 21; i > valorRsRetiro.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsRetiro);
                String valorDsRetiro = String.format("%.2f", balanceDto.getTotalRetiroDs());
                escpos.write("Dolares D$: ");
                for (int i = 20; i > valorDsRetiro.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsRetiro);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE GASTO");
                String valorGsGasto = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalGastoGs().intValue());
                escpos.write("Guaranies G$: ");
                for (int i = 18; i > valorGsGasto.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsGasto);
                String valorRsGasto = String.format("%.2f", balanceDto.getTotalGastoRs());
                escpos.write("Reales R$: ");
                for (int i = 21; i > valorRsGasto.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsGasto);
                String valorDsGasto = String.format("%.2f", balanceDto.getTotalGastoDs());
                escpos.write("Dolares D$: ");
                for (int i = 20; i > valorDsGasto.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsGasto);
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "DIFERENCIA");
                String valorGsDiferencia = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getDiferenciaGs().intValue());
                escpos.write("Guaranies G$: ");
                for (int i = 18; i > valorGsDiferencia.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsDiferencia);
                String valorRsDiferencia = String.format("%.2f", balanceDto.getDiferenciaRs());
                escpos.write("Reales R$: ");
                for (int i = 21; i > valorRsDiferencia.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsDiferencia);
                String valorDsDiferencia = String.format("%.2f", balanceDto.getDiferenciaDs());
                escpos.write("Dolares D$: ");
                for (int i = 20; i > valorDsDiferencia.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsDiferencia);
//                escpos.writeLF("--------------------------------");
//                escpos.writeLF(center, "VENTA TOTAL");
//                String valorGsVenta = NumberFormat.getNumberInstance(Locale.GERMAN).format(balanceDto.getTotalVentaGs().intValue());
//                escpos.write("Guaranies G$: ");
//                for (int i = 18; i > valorGsVenta.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorGsVenta);
//                String valorRsVenta = String.format("%.2f", balanceDto.getTotalVentaRs());
//                escpos.write("Reales R$: ");
//                for (int i = 21; i > valorRsVenta.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorRsVenta);
//                String valorDsVenta = String.format("%.2f", balanceDto.getTotalVentaDs());
//                escpos.write("Dolares D$: ");
//                for (int i = 20; i > valorDsVenta.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorDsVenta);
                escpos.writeLF("--------------------------------");
                escpos.feed(4);
                escpos.writeLF(center, ".......................");
                escpos.writeLF(center, "FIRMA");
                if (balanceDto.getUsuario().getPersona().getNombre().length() > 23) {
                    escpos.writeLF(center, balanceDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF(center, balanceDto.getUsuario().getPersona().getNombre());
                }
                escpos.feed(5);
                escpos.close();
                printerOutputStream.close();
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }


    public void printGasto(GastoDto gastoDto, String printerName, String local) {
        try {
            selectedPrintService = printingService.getPrintService(printerName);
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
                // creating the EscPosImage, need buffered image and algorithm.
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
                //Styles
                Style center = new Style().setJustification(EscPosConst.Justification.Center);

                QRCode qrCode = new QRCode();

                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
                imageBufferedImage = resize(imageBufferedImage, 200, 100);
                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
                EscPos escpos = new EscPos(printerOutputStream);
                Bitonal algorithm = new BitonalThreshold();
                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                imageWrapper.setJustification(EscPosConst.Justification.Center);
                escpos.write(imageWrapper, escposImage);
                if (sucursalService.sucursalActual() != null) {
                    escpos.writeLF(center, "Suc: " + sucursalService.sucursalActual().getNombre());
                }
                if (local != null) {
                    escpos.writeLF(center, "Local: " + local);
                }
                escpos.writeLF(center.setBold(true), "Gasto: " + gastoDto.getId());
                escpos.writeLF(center.setBold(true), "Caja: " + gastoDto.getCajaId());
                if (gastoDto.getUsuario().getPersona().getNombre().length() > 23) {
                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha " + gastoDto.getFecha().format(formatter));
                escpos.writeLF(new Style().setBold(true), "Tipo " + gastoDto.getTipoGasto().getId() + " - " + gastoDto.getTipoGasto().getDescripcion().toUpperCase());
                if (gastoDto.getObservacion() != null) {
                    escpos.writeLF("Obs: " + gastoDto.getObservacion().toUpperCase());
                }
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE GASTO");
                escpos.write("Guaranies G$: ");
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(gastoDto.getRetiroGs().intValue());
                for (int i = 18; i > valorGsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsAper);
                escpos.write("Reales R$: ");
                String valorRsAper = String.format("%.2f", gastoDto.getRetiroRs());
                for (int i = 21; i > valorRsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsAper);
                escpos.write("Dolares D$: ");
                String valorDsAper = String.format("%.2f", gastoDto.getRetiroDs());
                for (int i = 20; i > valorDsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsAper);
                escpos.writeLF("--------------------------------");
                escpos.feed(4);
                escpos.writeLF(center, ".......................");
                escpos.writeLF(center, "FIRMA RESPONSABLE");
                if (gastoDto.getResponsable().getPersona().getNombre().length() > 23) {
                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre());
                }
                if (gastoDto.getAutorizadoPor() != null) {
                    escpos.writeLF("--------------------------------");
                    escpos.feed(4);
                    escpos.writeLF(center, ".......................");
                    escpos.writeLF(center, "AUTORIZACION");
                    if (gastoDto.getAutorizadoPor().getPersona().getNombre().length() > 23) {
                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre().substring(0, 23));
                    } else {
                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre());
                    }
                }
                escpos.feed(5);
                escpos.close();
                printerOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printVueltoGasto(GastoDto gastoDto) {
//        try {
//            printService = PrinterOutputStream.getPrintServiceByName("TICKET58");
//            if (printService != null) {
//                printerOutputStream = new PrinterOutputStream(printService);
//                // creating the EscPosImage, need buffered image and algorithm.
//                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//                //Styles
//                Style center = new Style().setJustification(EscPosConst.Justification.Center);
//
//                QRCode qrCode = new QRCode();
//
//                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
//                imageBufferedImage = resize(imageBufferedImage, 200, 100);
//                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
//                EscPos escpos = new EscPos(printerOutputStream);
//                Bitonal algorithm = new BitonalThreshold();
//                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
//                imageWrapper.setJustification(EscPosConst.Justification.Center);
//                escpos.write(imageWrapper, escposImage);
//                if (sucursalService.sucursalActual() != null) {
//                    escpos.writeLF(center, "Suc: " + sucursalService.sucursalActual().getNombre());
//                }
//                escpos.writeLF(center.setBold(true), "Gasto: " + gastoDto.getId());
//                if (gastoDto.getUsuario().getPersona().getNombre().length() > 23) {
//                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre().substring(0, 23));
//                } else {
//                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre());
//                }
//                escpos.writeLF("Fecha " + gastoDto.getFecha().format(formatter));
//                escpos.writeLF(new Style().setBold(true), "Tipo " + gastoDto.getTipoGasto().getId() + " - " + gastoDto.getTipoGasto().getDescripcion().toUpperCase());
//                if (gastoDto.getObservacion() != null) {
//                    escpos.writeLF("Obs: " + gastoDto.getObservacion().toUpperCase());
//                }
//                escpos.writeLF("--------------------------------");
//                escpos.writeLF(center, "VALORES DE GASTO");
//                escpos.write("Guaranies G$: ");
//                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(gastoDto.getRetiroGs().intValue());
//                for (int i = 18; i > valorGsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorGsAper);
//                escpos.write("Reales R$: ");
//                String valorRsAper = String.format("%.2f", gastoDto.getRetiroRs());
//                for (int i = 21; i > valorRsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorRsAper);
//                escpos.write("Dolares D$: ");
//                String valorDsAper = String.format("%.2f", gastoDto.getRetiroDs());
//                for (int i = 20; i > valorDsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorDsAper);
//                escpos.writeLF("--------------------------------");
//                escpos.feed(4);
//                escpos.writeLF(center, ".......................");
//                escpos.writeLF(center, "FIRMA RESPONSABLE");
//                if (gastoDto.getResponsable().getPersona().getNombre().length() > 23) {
//                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre().substring(0, 23));
//                } else {
//                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre());
//                }
//                if (gastoDto.getAutorizadoPor() != null) {
//                    escpos.writeLF("--------------------------------");
//                    escpos.feed(4);
//                    escpos.writeLF(center, ".......................");
//                    escpos.writeLF(center, "AUTORIZACION");
//                    if (gastoDto.getAutorizadoPor().getPersona().getNombre().length() > 23) {
//                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre().substring(0, 23));
//                    } else {
//                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre());
//                    }
//                }
//                escpos.feed(5);
//                escpos.close();
//                printerOutputStream.close();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    public void printRetiro(RetiroDto retiroDto, String printerName, String local) {
        try {
            selectedPrintService = printingService.getPrintService(printerName);
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
                // creating the EscPosImage, need buffered image and algorithm.
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
                //Styles
                Style center = new Style().setJustification(EscPosConst.Justification.Center);

                QRCode qrCode = new QRCode();

                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
                imageBufferedImage = resize(imageBufferedImage, 200, 100);
                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
                EscPos escpos = new EscPos(printerOutputStream);
                Bitonal algorithm = new BitonalThreshold();
                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
                imageWrapper.setJustification(EscPosConst.Justification.Center);
                escpos.write(imageWrapper, escposImage);
                if (sucursalService.sucursalActual() != null) {
                    escpos.writeLF(center, "Suc: " + sucursalService.sucursalActual().getNombre());
                }
                if (local != null) {
                    escpos.writeLF(center, "Local: " + local);
                }
                escpos.writeLF(center.setBold(true), "Retiro: " + retiroDto.getId());
                escpos.writeLF(center.setBold(true), "Caja: " + retiroDto.getCajaId());
                if (retiroDto.getUsuario().getPersona().getNombre().length() > 23) {
                    escpos.writeLF("Cajero: " + retiroDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + retiroDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha " + retiroDto.getFecha().format(formatter));
                escpos.writeLF("--------------------------------");
                escpos.writeLF(center, "VALORES DE RETIRO");
                escpos.write("Guaranies G$: ");
                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(retiroDto.getRetiroGs().intValue());
                for (int i = 18; i > valorGsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorGsAper);
                escpos.write("Reales R$: ");
                String valorRsAper = String.format("%.2f", retiroDto.getRetiroRs());
                for (int i = 21; i > valorRsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorRsAper);
                escpos.write("Dolares D$: ");
                String valorDsAper = String.format("%.2f", retiroDto.getRetiroDs());
                for (int i = 20; i > valorDsAper.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorDsAper);
                escpos.writeLF("--------------------------------");
                escpos.feed(4);
                escpos.writeLF(center, ".......................");
                escpos.writeLF(center, "FIRMA RESPONSABLE");
                if (retiroDto.getResponsable().getPersona().getNombre().length() > 23) {
                    escpos.writeLF(center, retiroDto.getResponsable().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF(center, retiroDto.getResponsable().getPersona().getNombre());
                }
                escpos.feed(5);
                escpos.close();
                printerOutputStream.close();
            }
        } catch (IOException e) {

        }
    }

//    public void printVueltoGasto(GastoDto gastoDto){
//        try {
//            printService = PrinterOutputStream.getPrintServiceByName("TICKET58");
//            if(printService!=null){
//                printerOutputStream  = new PrinterOutputStream(printService);
//                // creating the EscPosImage, need buffered image and algorithm.
//                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//                //Styles
//                Style center = new Style().setJustification(EscPosConst.Justification.Center);
//
//                QRCode qrCode = new QRCode();
//
//                BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
//                imageBufferedImage = resize(imageBufferedImage, 200, 100);
//                RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
//                EscPos escpos = new EscPos(printerOutputStream);
//                Bitonal algorithm = new BitonalThreshold();
//                EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
//                imageWrapper.setJustification(EscPosConst.Justification.Center);
//                escpos.write(imageWrapper, escposImage);
//                escpos.writeLF(center.setBold(true), "SUC. CENTRO");
//                escpos.writeLF(center, "Salto del Guairá");
//                escpos.writeLF(center.setBold(true), "Gasto: "+ gastoDto.getId());
//                if(gastoDto.getUsuario().getPersona().getNombre().length() > 23){
//                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre().substring(0, 23));
//                } else {
//                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre());
//                }
//                escpos.writeLF("Fecha "+ gastoDto.getFecha().format(formatter));
//                escpos.writeLF(new Style().setBold(true) ,"Tipo "+ gastoDto.getTipoGasto().getId() +" - "+ gastoDto.getTipoGasto().getDescripcion().toUpperCase());
//                if(gastoDto.getObservacion()!=null){
//                    escpos.writeLF("Obs: " + gastoDto.getObservacion().toUpperCase());
//                }
//                escpos.writeLF("--------------------------------");
//                escpos.writeLF(center, "VALORES DE GASTO");
//                escpos.write("Guaranies G$: ");
//                String valorGsAper = NumberFormat.getNumberInstance(Locale.GERMAN).format(gastoDto.getRetiroGs().intValue());
//                for (int i = 18; i > valorGsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorGsAper);
//                escpos.write("Reales R$: ");
//                String valorRsAper = String.format("%.2f", gastoDto.getRetiroRs());
//                for (int i = 21; i > valorRsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorRsAper);
//                escpos.write("Dolares D$: ");
//                String valorDsAper = String.format("%.2f", gastoDto.getRetiroDs());
//                for (int i = 20; i > valorDsAper.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(valorDsAper);
//                escpos.writeLF("--------------------------------");
//                escpos.feed(4);
//                escpos.writeLF(center, ".......................");
//                escpos.writeLF(center, "FIRMA RESPONSABLE");
//                if(gastoDto.getResponsable().getPersona().getNombre().length() > 23){
//                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre().substring(0, 23));
//                } else {
//                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre());
//                }
//                if(gastoDto.getAutorizadoPor()!=null){
//                    escpos.writeLF("--------------------------------");
//                    escpos.feed(4);
//                    escpos.writeLF(center, ".......................");
//                    escpos.writeLF(center, "AUTORIZACION");
//                    if(gastoDto.getAutorizadoPor().getPersona().getNombre().length() > 23){
//                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre().substring(0, 23));
//                    } else {
//                        escpos.writeLF(center, gastoDto.getAutorizadoPor().getPersona().getNombre());
//                    }
//                }
//                escpos.feed(5);
//                escpos.close();
//                printerOutputStream.close();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

//    public Boolean printTicket58mmFactura(FacturaDto facturaDto, TimbradoDetalle timbradoDetalle, String printerName, String local) throws Exception {
//        Boolean ok = null;
//        PrintService selectedPrintService = printingService.getPrintService(printerName);
//
//        if (sucursal == null) {
//            sucursal = sucursalService.sucursalActual();
//        }
//
//        Double descuento = 0.0;
//        Double aumento = 0.0;
//        Double vueltoGs = 0.0;
//        Double vueltoRs = 0.0;
//        Double vueltoDs = 0.0;
//        Double pagadoGs = 0.0;
//        Double pagadoRs = 0.0;
//        Double pagadoDs = 0.0;
//
//        if (selectedPrintService != null) {
//            printerOutputStream = new PrinterOutputStream(selectedPrintService);
//            // creating the EscPosImage, need buffered image and algorithm.
//            //Styles
//            Style center = new Style().setJustification(EscPosConst.Justification.Center);
//            Style factura = new Style().setJustification(EscPosConst.Justification.Center).setFontSize(Style.FontSize._1, Style.FontSize._1);
//            QRCode qrCode = new QRCode();
//
//            BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
//            imageBufferedImage = resize(imageBufferedImage, 200, 100);
//            RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
//            EscPos escpos = null;
//            escpos = new EscPos(printerOutputStream);
//            Bitonal algorithm = new BitonalThreshold();
//            EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
//            imageWrapper.setJustification(EscPosConst.Justification.Center);
//            escpos.write(imageWrapper, escposImage);
//            escpos.writeLF(factura, timbradoDetalle.getTimbrado().getRazonSocial());
//            escpos.writeLF(factura, "RUC: " + timbradoDetalle.getTimbrado().getRuc());
//            escpos.writeLF(factura, "Timbrado: " + timbradoDetalle.getTimbrado().getNumero());
//            escpos.writeLF(factura, "De " + timbradoDetalle.getTimbrado().getFechaInicio().format(shortDate) + " a " + timbradoDetalle.getTimbrado().getFechaFin().format(shortDate));
//            escpos.writeLF(factura, "Nro: " + sucursalService.sucursalActual().getCodigoEstablecimientoFactura() + "-" + timbradoDetalle.getPuntoExpedicion() + "-" + timbradoDetalle.getNumeroActual() + 1);
//
//            if (sucursal != null) {
//                escpos.writeLF(center, "Suc: " + sucursal.getNombre());
//                if (sucursal.getCiudad() != null) {
//                    escpos.writeLF(center, sucursal.getCiudad().getDescripcion());
//                    escpos.writeLF(center, sucursal.getDireccion());
//                }
//            }
//            if (local != null) {
//                escpos.writeLF(center, "Local: " + local);
//            }
//            if(facturaDto.getVenta()!=null) escpos.writeLF(center.setBold(true), "Venta: " + facturaDto.get);
//
//            if (venta.getUsuario().getPersona().getNombre().length() > 23) {
//                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre().substring(0, 23));
//
//            } else {
//                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre());
//            }
//
//            escpos.writeLF("Fecha: " + venta.getCreadoEn().format(formatter));
//            escpos.writeLF("--------------------------------");
//
//            if (venta.getCliente() != null) {
////            escpos.writeLF("Cliente: " + venta.getCliente().getPersona().getNombre());
////            escpos.writeLF("CI/RUC: " + venta.getCliente().getPersona().getDocumento());
////            escpos.writeLF("Dir: " + venta.getCliente().getPersona().getDireccion());
//            }
//            escpos.writeLF("Cliente: MARIO JOSE AYALA NUNEZ");
//            escpos.writeLF("CI/RUC: 4987849-7");
//            escpos.writeLF("Dir: ");
//            escpos.writeLF("--------------------------------");
//
//            escpos.writeLF("Producto");
//            escpos.writeLF("Cant  IVA   P.U              P.T");
//            escpos.writeLF("--------------------------------");
//            for (VentaItem vi : ventaItemList) {
//                String cantidad = vi.getCantidad().intValue() + " (" + vi.getPresentacion().getCantidad().intValue() + ") " + "10%";
//                escpos.writeLF(vi.getProducto().getDescripcion());
//                escpos.write(new Style().setBold(true), cantidad);
//                String valorUnitario = NumberFormat.getNumberInstance(Locale.GERMAN).format(vi.getPrecioVenta().getPrecio().intValue());
//                String valorTotal = String.valueOf(vi.getPrecioVenta().getPrecio().intValue() * vi.getCantidad().intValue());
//                for (int i = 14; i > cantidad.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.write(valorUnitario);
//                for (int i = 16 - valorUnitario.length(); i > valorTotal.length(); i--) {
//                    escpos.write(" ");
//                }
//                escpos.writeLF(NumberFormat.getNumberInstance(Locale.GERMAN).format(vi.getPrecioVenta().getPrecio().intValue() * vi.getCantidad().intValue()));
//            }
//            escpos.writeLF("--------------------------------");
//            escpos.write("Total Gs: ");
//            String valorGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(venta.getTotalGs().intValue());
//            for (int i = 22; i > valorGs.length(); i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF(new Style().setBold(true).setFontSize(Style.FontSize._0, Style.FontSize._0), valorGs);
//            escpos.write("Total Rs: ");
//            String valorRs = String.format("%.2f", venta.getTotalRs());
//            for (int i = 22; i > valorGs.length(); i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF(valorRs);
//            escpos.write("Total Ds: ");
////      String valorDs = NumberFormat.getNumberInstance(new Locale("sk", "SK")).format(venta.getTotalDs());
//            String valorDs = String.format("%.2f", venta.getTotalDs());
//            for (int i = 22; i > valorGs.length(); i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF(valorDs);
//            escpos.writeLF("--------------------------------");
//            escpos.write("Gravadas 10%:");
//            for (int i = 19; i > valorGs.length(); i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF(valorGs);
//            escpos.write("Gravadas 5%: ");
//            for (int i = 19; i > 1; i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF("0");
//            escpos.write("Exentas:     ");
//            for (int i = 19; i > 1; i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF("0");
//            escpos.writeLF("--------Liquidación IVA---------");
//            escpos.write("Gravadas 10%:");
//            Double iva10 = venta.getTotalGs() / 11;
//            String iva10s = NumberFormat.getNumberInstance(Locale.GERMAN).format(iva10.intValue());
//            for (int i = 19; i > iva10s.length(); i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF(iva10s);
//            escpos.write("Gravadas 5%: ");
//            for (int i = 19; i > 1; i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF("0");
//            escpos.write("Total IVA:   ");
//            for (int i = 19; i > iva10s.length(); i--) {
//                escpos.write(" ");
//            }
//            escpos.writeLF(iva10s);
//            escpos.writeLF("--------------------------------");
//            if (sucursal != null && sucursal.getNroDelivery() != null) {
//                escpos.write(center, "Delivery? Escaneá el código qr o escribinos al ");
//                escpos.writeLF(center, sucursal.getNroDelivery());
//            }
//            if (sucursal.getNroDelivery() != null) {
//                escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center), "wa.me/" + sucursal.getNroDelivery());
//            }
//            escpos.feed(1);
//            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
////            escpos.writeLF("--------------------------------");
////            escpos.write( "Conservar este papel ");
//            escpos.feed(5);
//
//            try {
//                escpos.close();
//                printerOutputStream.close();
//                ok = true;
//            } catch (IOException ioe) {
//                ioe.printStackTrace();
//                ok = false;
//            }
//        }
//        return ok;
//    }
}
