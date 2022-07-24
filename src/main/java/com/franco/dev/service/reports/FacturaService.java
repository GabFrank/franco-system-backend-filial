package com.franco.dev.service.reports;

import com.franco.dev.domain.financiero.FacturaDto;
import com.franco.dev.domain.financiero.VentaItemDto;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.GraphQLException;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FacturaService {

    private PrintService printService;
    private PrinterOutputStream printerOutputStream;
    @Autowired
    private ImageService imageService;

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
            facturaDto.setDireccion("Av. Paraguay c/ 30 de julio");
            List<VentaItemDto> ventaItemList = new ArrayList<>();
            ventaItemList.add(new VentaItemDto("5", "Brahma lata 269", "3.500", "120.000"));
            ventaItemList.add(new VentaItemDto("2", "Skol lata", "3.500", "7.000"));
            ventaItemList.add(new VentaItemDto("7", "Producto cualquiera", "8.500", "50.000"));

            File file = ResourceUtils.getFile("classpath:factura.jrxml");
            JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(ventaItemList);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("contado", facturaDto.getContado());
            parameters.put("credito", facturaDto.getCredito());
            parameters.put("fecha", facturaDto.getFecha());
            parameters.put("ivaTotal", facturaDto.getIvaParcial());
            parameters.put("nombre", facturaDto.getNombre());
            parameters.put("ruc", facturaDto.getRuc());
            parameters.put("totalFinal", facturaDto.getTotal());
            parameters.put("totalEnLetras", facturaDto.getTotalEnLetras());
            parameters.put("direccion", facturaDto.getDireccion());

            JasperPrint jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            jasperPrint1.setPageHeight(842);

            List<VentaItemDto> ventaItemList2 = new ArrayList<>();
            ventaItemList.add(new VentaItemDto("5", "Brahma lata 269", "3.500", "120.000"));
            ventaItemList.add(new VentaItemDto("2", "Skol lata", "3.500", "7.000"));
            ventaItemList.add(new VentaItemDto("7", "Producto cualquiera", "8.500", "50.000"));

            File file2 = ResourceUtils.getFile("classpath:factura2.jrxml");
            JasperReport jasperReport2 = JasperCompileManager.compileReport(file.getAbsolutePath());
            JRBeanCollectionDataSource dataSource2 = new JRBeanCollectionDataSource(ventaItemList);
            Map<String, Object> parameters2 = new HashMap<>();
            parameters2.put("contado", facturaDto.getContado());
            parameters2.put("credito", facturaDto.getCredito());
            parameters2.put("fecha", facturaDto.getFecha());
            parameters2.put("ivaTotal", facturaDto.getIvaParcial());
            parameters2.put("nombre", facturaDto.getNombre());
            parameters2.put("ruc", facturaDto.getRuc());
            parameters2.put("totalFinal", facturaDto.getTotal());
            parameters2.put("totalEnLetras", facturaDto.getTotalEnLetras());
            parameters2.put("direccion", facturaDto.getDireccion());

            JasperPrint jasperPrint2 = JasperFillManager.fillReport(jasperReport2, parameters2, dataSource2);

            JRPrintPage page2 = jasperPrint2.getPages().get(0);
            List<JRPrintElement> elements = page2.getElements();

            for(JRPrintElement e: elements){
                e.setY(e.getY() + 421);
                jasperPrint1.getPages().get(0).addElement(e);
            }

            OutputStream output;
            output = new FileOutputStream(new File("/Users/gabfranck/Desktop/prueba.pdf"));
            JasperExportManager.exportReportToPdfStream(jasperPrint1, output);
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
