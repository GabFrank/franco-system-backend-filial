package com.franco.dev.service.utils;

import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;


@Service
public class PrintingService {


    private Logger log = LoggerFactory.getLogger(PrintingService.class);
    private List<PrintService> printServiceList = new ArrayList<>();
    private PrinterOutputStream printerOutputStream;
    private PrintService selectedPrintService;

    PrintingService() {
    }

    public static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        // Create a buffered image with transparency
        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }

    public static BufferedImage resize(BufferedImage img, int newW, int newH) {
        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return dimg;
    }

    public static BufferedImage base64StringToImage(String imageString) {
        BufferedImage image = null;
        byte[] imageByte;
        try {/*from   w w  w .j a  v  a  2  s. c o m*/
            imageByte = Base64.getDecoder().decode(imageString);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageByte);
            image = ImageIO.read(bis);
            bis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return image;
    }

    public PrintService getPrintService(String printerName) {
        PrintService ps = searchPrinter(printerName);
        if(ps!=null){
            selectedPrintService = ps;
            return ps;
        } else {
            return setPrintService(printerName);
        }
    }

    public PrintService setPrintService(String printerName) {
        PrintService ps = null;
        try {
            ps = PrinterOutputStream.getPrintServiceByName(printerName);
            selectedPrintService = ps;
            printServiceList.add(ps);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ps;
    }

    public PrintService getLasUsedPrinter(){
        return selectedPrintService;
    }

    public PrintService searchPrinter(String printerName) {
        return printServiceList.stream()
                .filter(x -> printerName.equals(x))
                .findAny()
                .orElse(null);
    }

    //    tamanho de papel 58mm entran 32 caracteres
    public Boolean printTicketCaja58mm(String image, String printerName) {
        PrintService selectedPrintService = null;
//        LocalDateTime date = data.getVenta().getCreadoEn();
//        String fecha = date.getDayOfMonth()+"/"+date.getMonthValue()+"/"+date.getYear();
//        StringBuilder stringBuilder = new StringBuilder();
//        stringBuilder.append(date.getHour()).append(":").append(date.getMinute()).append(":").append(date.getSecond());
//        String hora =  stringBuilder.toString();

        try {
            selectedPrintService = getPrintService(printerName);
            if (selectedPrintService == null) {
                selectedPrintService = setPrintService(printerName);
            }
            if (selectedPrintService != null) {
                printerOutputStream = new PrinterOutputStream(selectedPrintService);
            } else {
                return false;
            }
            // creating the EscPosImage, need buffered image and algorithm.

            BufferedImage imageBufferedImage = base64StringToImage(image);
            imageBufferedImage = resize(imageBufferedImage, 200, 100);
            ImageHelper helper = new ImageHelper();
            BitImageWrapper imageWrapper = new BitImageWrapper();
            EscPos escpos = new EscPos(printerOutputStream);
            Bitonal algorithm = new BitonalThreshold();
//            EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
            imageWrapper.setJustification(EscPosConst.Justification.Center);
//            escpos.write(imageWrapper, escposImage);
            helper.write(escpos, new CoffeeImageImpl(imageBufferedImage),imageWrapper,algorithm);
            escpos.feed(2);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();
            printerOutputStream.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

}
