package com.franco.dev.service.reports;

import com.franco.dev.domain.financiero.FacturaDto;
import com.franco.dev.domain.financiero.VentaItemDto;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.GraphQLException;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FacturaService {

    private PrintService printService;
    private PrinterOutputStream printerOutputStream;

    public void generarFactura() {
        try {
            FacturaDto facturaDto = new FacturaDto();
            facturaDto.setContado("X");
            facturaDto.setFecha("10/12/2022");
            facturaDto.setIvaParcial("33.500");
            facturaDto.setNombre("Gabriel Francisco Franco Arevalos");
            facturaDto.setRuc("4043581-4");
            facturaDto.setTotal("350.000");
            facturaDto.setTotalEnLetras("Trescientos cincuenta mil");
            List<VentaItemDto> ventaItemList = new ArrayList<>();
            ventaItemList.add(new VentaItemDto("5", "Brahma lata 269", "3.500", "120.000"));
            ventaItemList.add(new VentaItemDto("2", "Skol lata", "3.500", "7.000"));
            ventaItemList.add(new VentaItemDto("7", "Producto cualquiera", "8.500", "50.000"));

            File file = ResourceUtils.getFile("classpath:factura.jrxml");
            JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(ventaItemList);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("contado", "X");
            parameters.put("credito", "");
            parameters.put("fecha", "10/12/2022");
            parameters.put("ivaTotal", "33.500");
            parameters.put("nombre", "Gabriel Francisco Franco Arevalos");
            parameters.put("ruc", "4043581-4");
            parameters.put("totalFinal", "350.000");
            parameters.put("totalEnLetras", "Trescientos cincuenta mil");
            parameters.put("direccion", "Av. paraguay c/ 30 de julio");

            JasperPrint jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            jasperPrint1.setPageHeight(842);
            JRPrintPage page1 = jasperPrint1.getPages().get(0);
            List<JRPrintElement> elements = page1.getElements();
            List<JRPrintElement> newElements = new ArrayList<>();
            Integer lastIndex = elements.size();
            for (JRPrintElement element : elements) {
                newElements.add(element);
            }
            for (JRPrintElement element : elements) {
                Integer y = element.getY();
                y = y + 421;
                element.setY(y);
                newElements.add(element);
            }
            jasperPrint1.getPages().get(0).setElements(newElements);
            printFactura(jasperPrint1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
