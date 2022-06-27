package com.franco.dev.print.operaciones;

import com.franco.dev.domain.operaciones.Entrada;
import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.service.utils.TicketCajaData;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.barcode.BarCode;
import com.franco.dev.utilitarios.print.escpos.barcode.QRCode;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import org.apache.tomcat.jni.Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class MovimientoPrintService {

    private Logger log = LoggerFactory.getLogger(MovimientoPrintService.class);
    private PrintService printService;
    private PrinterOutputStream printerOutputStream;

    public Boolean entrada58mm(Entrada entrada, List<EntradaItem> entradaItemList , String printerName){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        String fechaActual = LocalDateTime.now().format(formatter);

//        StringBuilder fechaActualStr = new StringBuilder()
//                .append(fechaActual.getDayOfMonth()).append("-").append(fechaActual.getMonth())
//                .append("-").append(fechaActual.getYear()).append("  ").append(fechaActual.getHour())
//                .append(":").append(fechaActual)
        try {
            printService = PrinterOutputStream.getPrintServiceByName(printerName);
            printerOutputStream  = new PrinterOutputStream(printService);
            // creating the EscPosImage, need buffered image and algorithm.

//            BufferedImage imageBufferedImage = ImageIO.read(new File("/Users/gabfranck/workspace/franco-systems/backend-spring/images/empresarial/logos/logo_ticket1.png"));
//            imageBufferedImage = resize(imageBufferedImage, 210, 120);

//            log.info(imageBufferedImage.toString());
            // this wrapper uses esc/pos sequence: "GS 'v' '0'"
//            RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();

            Style header = new Style()
                    .setJustification(EscPosConst.Justification.Center);
            Style nroVenta = new Style();
            nroVenta.setFontSize(Style.FontSize._3, Style.FontSize._3);
            nroVenta.setBold(true);
            nroVenta.setJustification(EscPosConst.Justification.Center);
            Style monedas = new Style();
            monedas.setJustification(EscPosConst.Justification.Left_Default);
            Style totales = new Style();
            totales.setJustification(EscPosConst.Justification.Left_Default);
            Style footer = new Style();
            footer.setFontSize(Style.FontSize._5, Style.FontSize._5).setBold(true).setJustification(EscPosConst.Justification.Center);

            BarCode barcode = new BarCode();
            QRCode qrCode = new QRCode();
            qrCode.setSize(8);
            qrCode.setJustification(EscPosConst.Justification.Center);

            EscPos escpos = new EscPos(printerOutputStream);
            Bitonal algorithm = new BitonalThreshold();
//            EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
//            imageWrapper.setJustification(EscPosConst.Justification.Center);
//            escpos.write(imageWrapper, escposImage);
//            escpos.feed(1);
//            escpos.write(header, data.getSucursal().getNombre());
//            if(data.getSucursal().getDireccion()!=null){
//                escpos.feed(1);
//                escpos.write(header, data.getSucursal().getDireccion());
//            }
            escpos.feed(1);
            escpos.write(header, "BODEGA FRANCO");
            escpos.feed(1);
            escpos.write(header, "DEPOSITO");
            escpos.feed(1);
            escpos.write("Fecha: "+fechaActual);
            escpos.feed(1);
            if(entrada.getResponsableCarga()!=null) escpos.write("Usuario:"+entrada.getResponsableCarga().getPersona().getNombre().toUpperCase());
            escpos.feed(1);
            escpos.write("Movimiento: ENTRADA      ID: "+entrada.getId());
            escpos.feed(1);
            escpos.write("===========PRODUCTOS============");
            escpos.feed(1);
            for(EntradaItem ei: entradaItemList){
                escpos.write(ei.getId()+"-"+ei.getProducto().getDescripcion());
                escpos.feed(1);
                escpos.write(ei.getPresentacion().getDescripcion().toUpperCase()+ " x " + ei.getCantidad());
                escpos.feed(1);
                escpos.write("     ----------------------     ");
            }
            escpos.write("================================");
            escpos.feed(5);
            escpos.write("      --------------------      ");
            escpos.feed(1);
            escpos.write("        Firma Responsable       ");
            escpos.feed(3);
            escpos.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
