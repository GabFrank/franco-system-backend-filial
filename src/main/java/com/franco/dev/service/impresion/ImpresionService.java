package com.franco.dev.service.impresion;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.dto.GastoDto;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.barcode.QRCode;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.franco.dev.service.utils.PrintingService.resize;

@Service
public class ImpresionService {

    @Autowired
    private ImageService imageService;

    private PrintService printService;

    private PrinterOutputStream printerOutputStream;

    @Autowired
    private SucursalService sucursalService;

    public Boolean printBalance(PdvCajaBalanceDto balanceDto){
        try {
            printService = PrinterOutputStream.getPrintServiceByName("TICKET58");
            if(printService!=null){
                printerOutputStream  = new PrinterOutputStream(printService);
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
                if(balanceDto.getSucursal()!=null){
                    escpos.writeLF(center, "Suc: " + balanceDto.getSucursal().getNombre());
                }
                escpos.writeLF(center.setBold(true), "Caja: "+ balanceDto.getIdCaja());
                if(balanceDto.getUsuario().getPersona().getNombre().length() > 23){
                    escpos.writeLF("Cajero: " + balanceDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + balanceDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha Apertura: "+ balanceDto.getFechaApertura().format(formatter));
                escpos.writeLF("Fecha Cierre: "+ balanceDto.getFechaCierre().format(formatter));
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
                if(balanceDto.getUsuario().getPersona().getNombre().length() > 23){
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


    public void printGasto(GastoDto gastoDto){
        try {
            printService = PrinterOutputStream.getPrintServiceByName("TICKET58");
            if(printService!=null){
                printerOutputStream  = new PrinterOutputStream(printService);
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
                if(sucursalService.sucursalActual()!=null){
                    escpos.writeLF(center, "Suc: " + sucursalService.sucursalActual().getNombre());
                }
                escpos.writeLF(center.setBold(true), "Gasto: "+ gastoDto.getId());
                escpos.writeLF(center.setBold(true), "Caja: "+ gastoDto.getCajaId());
                if(gastoDto.getUsuario().getPersona().getNombre().length() > 23){
                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha "+ gastoDto.getFecha().format(formatter));
                escpos.writeLF(new Style().setBold(true) ,"Tipo "+ gastoDto.getTipoGasto().getId() +" - "+ gastoDto.getTipoGasto().getDescripcion().toUpperCase());
                if(gastoDto.getObservacion()!=null){
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
                if(gastoDto.getResponsable().getPersona().getNombre().length() > 23){
                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre());
                }
                if(gastoDto.getAutorizadoPor()!=null){
                    escpos.writeLF("--------------------------------");
                    escpos.feed(4);
                    escpos.writeLF(center, ".......................");
                    escpos.writeLF(center, "AUTORIZACION");
                    if(gastoDto.getAutorizadoPor().getPersona().getNombre().length() > 23){
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

    public void printVueltoGasto(GastoDto gastoDto){
        try {
            printService = PrinterOutputStream.getPrintServiceByName("TICKET58");
            if(printService!=null){
                printerOutputStream  = new PrinterOutputStream(printService);
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
                if(sucursalService.sucursalActual()!=null){
                    escpos.writeLF(center, "Suc: " + sucursalService.sucursalActual().getNombre());
                }
                escpos.writeLF(center.setBold(true), "Gasto: "+ gastoDto.getId());
                if(gastoDto.getUsuario().getPersona().getNombre().length() > 23){
                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + gastoDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha "+ gastoDto.getFecha().format(formatter));
                escpos.writeLF(new Style().setBold(true) ,"Tipo "+ gastoDto.getTipoGasto().getId() +" - "+ gastoDto.getTipoGasto().getDescripcion().toUpperCase());
                if(gastoDto.getObservacion()!=null){
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
                if(gastoDto.getResponsable().getPersona().getNombre().length() > 23){
                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF(center, gastoDto.getResponsable().getPersona().getNombre());
                }
                if(gastoDto.getAutorizadoPor()!=null){
                    escpos.writeLF("--------------------------------");
                    escpos.feed(4);
                    escpos.writeLF(center, ".......................");
                    escpos.writeLF(center, "AUTORIZACION");
                    if(gastoDto.getAutorizadoPor().getPersona().getNombre().length() > 23){
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

    public void printRetiro(RetiroDto retiroDto){
        try {
            printService = PrinterOutputStream.getPrintServiceByName("TICKET58");
            if(printService!=null){
                printerOutputStream  = new PrinterOutputStream(printService);
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
                if(sucursalService.sucursalActual()!=null){
                    escpos.writeLF(center, "Suc: " + sucursalService.sucursalActual().getNombre());
                }
                escpos.writeLF(center.setBold(true), "Retiro: "+ retiroDto.getId());
                escpos.writeLF(center.setBold(true), "Caja: "+ retiroDto.getCajaId());
                if(retiroDto.getUsuario().getPersona().getNombre().length() > 23){
                    escpos.writeLF("Cajero: " + retiroDto.getUsuario().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF("Cajero: " + retiroDto.getUsuario().getPersona().getNombre());
                }
                escpos.writeLF("Fecha "+ retiroDto.getFecha().format(formatter));
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
                if(retiroDto.getResponsable().getPersona().getNombre().length() > 23){
                    escpos.writeLF(center, retiroDto.getResponsable().getPersona().getNombre().substring(0, 23));
                } else {
                    escpos.writeLF(center, retiroDto.getResponsable().getPersona().getNombre());
                }
                escpos.feed(5);
                escpos.close();
                printerOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
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
}
