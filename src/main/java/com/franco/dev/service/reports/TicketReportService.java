package com.franco.dev.service.reports;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.graphql.operaciones.VentaItemTicketData;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import lombok.Data;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

@Service
public class TicketReportService {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private VentaItemService ventaItemService;

    public String exportReport(Long id){
        String status = "ndoikoi";
        Venta venta = ventaService.findById(id).orElse(null);
        List<VentaItem> ventaItemList = ventaItemService.findByVentaId(id);
        ReportDataSource rds = new ReportDataSource();
        rds.venta = venta;
        rds.ventaItemList = ventaItemList;
        List<VentaItemTicketData> dataList = new ArrayList<>();
        for(VentaItem vi : ventaItemList){
            VentaItemTicketData vitd = new VentaItemTicketData();
            vitd.setCodigo(vi.getId().toString());
            vitd.setDescripcion(vi.getProducto().getDescripcion());
            vitd.setCantidad(vi.getCantidad().toString());
            vitd.setIva("10%");
            vitd.setPrecioVenta(vi.getPrecioVenta().toString());
            vitd.setValorTotal("100000");
            dataList.add(vitd);
        }
        // cargar archivo y compilar
        File file = null;
        try {
            file = ResourceUtils.getFile("classpath:reports/ticket-58mm.jrxml");
            try {
                JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
                JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(dataList);
                Map<String , Object> parameters = new HashMap<>();
                parameters.put("nro_venta", venta.getId().toString());
                parameters.put("fecha", venta.getCreadoEn().toString());
                parameters.put("vendedor", venta.getUsuario().getPersona().getNombre());
                parameters.put("cliente", venta.getCliente().getPersona().getNombre());
                parameters.put("ruc", venta.getCliente().getPersona().getDocumento());
                parameters.put("barCode", venta.getId() + "154324335528");
                JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
                JasperExportManager.exportReportToPdfFile(jasperPrint, "/Users/gabfranck/Desktop/" + venta.getCreadoEn() + "-" + venta.getId() + ".pdf");
                status = "oikoite";
                PrintRequestAttributeSet printRequestAttributeSet = new HashPrintRequestAttributeSet();

//                printRequestAttributeSet.add();

            } catch (JRException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return status;
    }
}

class ReportDataSource {
    Venta venta;
    List<VentaItem> ventaItemList;
}

