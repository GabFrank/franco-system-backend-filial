package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.service.empresarial.PuntoDeVentaService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.sifen.service.SifenService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.franco.dev.service.impresion.ImpresionService.shortDateTime;
import static com.franco.dev.service.utils.PrintingService.resize;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;
import org.springframework.transaction.annotation.Transactional;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    @Autowired
    private VentaItemService ventaItemService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private PresentacionService presentacionService;
    @Autowired
    private FacturaLegalService facturaLegalService;
    @Autowired
    private FacturaLegalItemService facturaLegalItemService;

    private static final Logger log = LoggerFactory.getLogger(FacturaService.class);

    @Autowired
    private SifenService sifenService;

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    public DecimalFormat df = new DecimalFormat("#,###.##");

    public void printTicket58mmFactura(FacturaLegal facturaLegal, String printerName) throws Exception {
        PrintService selectedPrintService = printingService.getPrintService(printerName);
        Sucursal sucursal = sucursalService.findById(facturaLegal.getSucursalId()).orElse(null);
        Cliente cliente = facturaLegal.getCliente();
        TimbradoDetalle timbradoDetalle = facturaLegal.getTimbradoDetalle();
        Usuario cajero = facturaLegal.getUsuario();
        // Usar los datos ya calculados en FacturaLegal
        Double totalFinal = facturaLegal.getTotalFinal();
        Double totalIva10 = facturaLegal.getIvaParcial10();
        Double totalIva5 = facturaLegal.getIvaParcial5();
        Double totalIva = totalIva10 + totalIva5;
        Double descuento = facturaLegal.getDescuento() != null ? facturaLegal.getDescuento() : 0.0;

        // Obtener los items de la factura
        List<FacturaLegalItem> facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(facturaLegal.getId());

        if (selectedPrintService != null) {
            printerOutputStream = new PrinterOutputStream(selectedPrintService);
            
            // Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center)
                    .setFontSize(Style.FontSize._1, Style.FontSize._1);
            QRCode qrCode = new QRCode();

            BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
            imageBufferedImage = resize(imageBufferedImage, 200, 100);
            BitImageWrapper imageWrapper = new BitImageWrapper();
            EscPos escpos = new EscPos(printerOutputStream);
            Bitonal algorithm = new BitonalThreshold();
            EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
            imageWrapper.setJustification(EscPosConst.Justification.Center);
            escpos.writeLF("--------------------------------");
            escpos.write(imageWrapper, escposImage);
            escpos.writeLF(factura, timbradoDetalle.getTimbrado().getRazonSocial().toUpperCase());
            escpos.writeLF(factura, "RUC: " + timbradoDetalle.getTimbrado().getRuc());
            escpos.writeLF(factura, "Timbrado: " + timbradoDetalle.getTimbrado().getNumero());
            escpos.writeLF(factura,
                    "De " + timbradoDetalle.getTimbrado().getFechaInicio().format(impresionService.shortDate) + " a "
                            + timbradoDetalle.getTimbrado().getFechaFin().format(impresionService.shortDate));
            
            StringBuilder numeroFacturaString = new StringBuilder();
            for (int i = 7; i > facturaLegal.getNumeroFactura().toString().length(); i--) {
                numeroFacturaString.append("0");
            }
                numeroFacturaString.append(facturaLegal.getNumeroFactura());
            
            escpos.writeLF(factura, "Nro: " + sucursal.getCodigoEstablecimientoFactura() + "-"
                    + timbradoDetalle.getPuntoExpedicion() + "-" + numeroFacturaString.toString());
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
            
            if (facturaLegal.getVenta() != null) {
                escpos.writeLF(center.setBold(true), "Venta: " + facturaLegal.getVenta().getId());
            }
            
            if (cajero != null) {
                escpos.writeLF("Cajero: " + cajero.getPersona().getNombre());
            }

            escpos.writeLF("Fecha: " + facturaLegal.getCreadoEn().format(shortDateTime));
            escpos.writeLF("--------------------------------");

            String nombreCliente = facturaLegal.getNombre().toUpperCase();
            nombreCliente = nombreCliente.replace("Ñ", "N")
                    .replace("Á", "A")
                    .replace("É", "E")
                    .replace("Í", "I")
                    .replace("Ó", "O")
                    .replace("Ú", "U");
            escpos.writeLF("Cliente: " + nombreCliente);

            if (facturaLegal.getRuc() != null && !facturaLegal.getRuc().contains("-")) {
                    facturaLegal.setRuc(facturaLegal.getRuc() + getDigitoVerificadorString(facturaLegal.getRuc()));
            }

            escpos.writeLF("CI/RUC: " + facturaLegal.getRuc());
            if (facturaLegal.getDireccion() != null)
                escpos.writeLF("Dir: " + facturaLegal.getDireccion());

            escpos.writeLF("--------------------------------");

            escpos.writeLF("Producto");
            escpos.writeLF("Cant  IVA   P.U              P.T");
            escpos.writeLF("--------------------------------");
            
            for (FacturaLegalItem vi : facturaLegalItemList) {
                Integer iva = null;
                if (vi.getPresentacion() != null) {
                    iva = vi.getPresentacion().getProducto().getIva();
                }
                    if (iva == null) {
                        iva = 10;
                    }
                String cantidad = vi.getCantidad().intValue() + " (" + vi.getCantidad() + ") " + iva + "%";
                    escpos.writeLF(vi.getDescripcion());
                escpos.write(new Style().setBold(true), cantidad);
                    String valorUnitario = df.format(vi.getPrecioUnitario().intValue());
                String valorTotal = df.format(vi.getTotal().intValue());
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
            
            // Mostrar desglose de ajuste (descuento o aumento) si existe
            if (descuento != 0) {
                Double totalSinAjuste = totalFinal - descuento; // Si descuento es negativo (aumento), esto suma
                escpos.write("Total parcial: ");
                String totalParcialGs = df.format(totalSinAjuste);
                for (int i = 17; i > totalParcialGs.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(totalParcialGs);
                
                if (descuento > 0) {
                    escpos.write("Descuento: ");
                    String descuentoGs = df.format(descuento);
                    for (int i = 22; i > descuentoGs.length(); i--) {
                    escpos.write(" ");
                }
                    escpos.writeLF(descuentoGs);
                } else {
                    escpos.write("Aumento: ");
                    String aumentoGs = df.format(Math.abs(descuento));
                    for (int i = 22; i > aumentoGs.length(); i--) {
                    escpos.write(" ");
                    }
                    escpos.writeLF(aumentoGs);
                }
            }
            
                escpos.write("Total Gs: ");
            String valorGs = df.format(totalFinal);
                for (int i = 22; i > valorGs.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(new Style().setBold(true), valorGs);

            escpos.writeLF("--------Liquidación IVA---------");
            escpos.write("Gravadas 10%:");
            String totalIva10S = df.format(totalIva10.intValue());
            for (int i = 19; i > totalIva10S.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva10S);
            escpos.write("Gravadas 5%: ");
            String totalIva5S = df.format(totalIva5.intValue());
            for (int i = 19; i > totalIva5S.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva5S);
            escpos.write("Exentas:     ");
            String totalIva0S = df.format(facturaLegal.getTotalParcial0().intValue());
            for (int i = 19; i > totalIva0S.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva0S);
            String totalFinalIvaS = df.format(totalIva.intValue());
            escpos.write("Total IVA:   ");
            for (int i = 19; i > totalFinalIvaS.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalFinalIvaS);

            escpos.writeLF("--------------------------------");
            
            // Generar CDC y QR usando datos del documento electrónico
            String cdc = facturaLegal.getCdc();
            String urlQr = facturaLegal.getUrlQr();

            if (urlQr != null) {
                escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center), urlQr);
            }

            escpos.writeLF(center,
                    "Consulte la validez de esta Factura Electrónica con el número de CDC impreso abajo en:");
            escpos.writeLF(center, "https://ekuatia.set.gov.py/consultas");

            if (cdc != null) {
                String cdcFormateado = cdc.replaceAll("\\s+", "");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cdcFormateado.length(); i += 4) {
                if (i > 0)
                    sb.append(" ");
                sb.append(cdcFormateado.substring(i, Math.min(i + 4, cdcFormateado.length())));
            }
            escpos.writeLF(center, sb.toString());
            }

            escpos.writeLF(center,
                    "ESTE DOCUMENTO ES UNA REPRESENTACION GRAFICA DE UN DOCUMENTO ELECTRONICO (XML)");
            escpos.writeLF("--------------------------------");

            escpos.feed(1);
            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
            escpos.feed(5);

            try {
                    escpos.close();
                    printerOutputStream.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } else {
            System.out.println("selectedPrintService is null");
        }
    }

    @Transactional
    public FacturaLegal crearFacturaLegalDesdeVenta(Venta venta, List<VentaItem> items, Long pdvId, List<CobroDetalleInput> cobroDetalleList) {
        FacturaLegal facturaLegal = new FacturaLegal();
        facturaLegal.setVenta(venta);
        facturaLegal.setCliente(venta.getCliente());
        facturaLegal.setCaja(venta.getCaja());
        facturaLegal.setFecha(venta.getCreadoEn() != null ? venta.getCreadoEn() : LocalDateTime.now());
        facturaLegal.setNombre(venta.getCliente() != null ? venta.getCliente().getPersona().getNombre() : "SIN NOMBRE");
        facturaLegal.setRuc(venta.getCliente() != null ? venta.getCliente().getPersona().getDocumento() : "X");

        // Calcular descuentos y aumentos de CobroDetalle
        Double descuentoTotal = 0.0;
        Double aumentoTotal = 0.0;
        if (cobroDetalleList != null) {
            for (CobroDetalleInput cdi : cobroDetalleList) {
                Double valorCalculado = cdi.getValor() * cdi.getCambio();
                if (cdi.getDescuento() != null && cdi.getDescuento()) {
                    descuentoTotal += valorCalculado;
                }
                if (cdi.getAumento() != null && cdi.getAumento()) {
                    aumentoTotal += valorCalculado;
                }
            }
        }
        
        // Calcular el ajuste neto (descuentos - aumentos)
        // Si es positivo = descuento neto, si es negativo = aumento neto
        Double ajusteNeto = descuentoTotal - aumentoTotal;

        Double totalParcial0 = 0.0;
        Double totalParcial5 = 0.0;
        Double totalParcial10 = 0.0;
        Double ivaParcial5 = 0.0;
        Double ivaParcial10 = 0.0;

        for (VentaItem item : items) {
            Double totalItem = item.getPrecio() * item.getCantidad();
            Integer iva = item.getProducto().getIva();
            if (iva != null) {
                switch (iva) {
                    case 10:
                        totalParcial10 += totalItem;
                        ivaParcial10 += totalItem / 11;
                        break;
                    case 5:
                        totalParcial5 += totalItem;
                        ivaParcial5 += totalItem / 21;
                        break;
                    default:
                        totalParcial0 += totalItem;
                        break;
                }
        } else {
                totalParcial0 += totalItem;
            }
        }

        // Aplicar ajuste (descuento o aumento) proporcionalmente a cada categoría de IVA
        Double totalSinAjuste = totalParcial0 + totalParcial5 + totalParcial10;
        
        if (ajusteNeto != 0 && totalSinAjuste > 0) {
            Double porcentajeAjuste = Math.abs(ajusteNeto) / totalSinAjuste;
            
            if (ajusteNeto > 0) {
                // Descuento: reducir los totales
                totalParcial0 = totalParcial0 * (1 - porcentajeAjuste);
                totalParcial5 = totalParcial5 * (1 - porcentajeAjuste);
                totalParcial10 = totalParcial10 * (1 - porcentajeAjuste);
            } else {
                // Aumento: incrementar los totales
                totalParcial0 = totalParcial0 * (1 + porcentajeAjuste);
                totalParcial5 = totalParcial5 * (1 + porcentajeAjuste);
                totalParcial10 = totalParcial10 * (1 + porcentajeAjuste);
            }
            
            // Recalcular IVA después del ajuste
            ivaParcial5 = totalParcial5 / 21;
            ivaParcial10 = totalParcial10 / 11;
        }

        facturaLegal.setTotalParcial0(totalParcial0);
        facturaLegal.setTotalParcial5(totalParcial5);
        facturaLegal.setTotalParcial10(totalParcial10);
        facturaLegal.setIvaParcial5(ivaParcial5);
        facturaLegal.setIvaParcial10(ivaParcial10);
        facturaLegal.setDescuento(ajusteNeto);
        facturaLegal.setTotalFinal(venta.getTotalGs());
        facturaLegal.setUsuario(venta.getUsuario());
        facturaLegal.setSucursalId(venta.getSucursalId());
        facturaLegal.setCredito(false);

        TimbradoDetalle timbradoDetalle = timbradoDetalleService.getTimbradoDetalleActual(pdvId);
        if (timbradoDetalle != null) {
            facturaLegal.setTimbradoDetalle(timbradoDetalle);
            Long numeroFactura = timbradoDetalle.getNumeroActual() != null ? timbradoDetalle.getNumeroActual() + 1 : timbradoDetalle.getNumeroActual();
            // numero factura es un integer
            facturaLegal.setNumeroFactura(numeroFactura.intValue());

            FacturaLegal facturaLegalGuardada = facturaLegalService.save(facturaLegal);

            for(VentaItem vi : items){
                FacturaLegalItem fli = new FacturaLegalItem();
                fli.setFacturaLegal(facturaLegalGuardada);
                fli.setVentaItem(vi);
                fli.setCantidad(vi.getCantidad().floatValue());
                fli.setDescripcion(vi.getProducto().getDescripcion());
                fli.setPrecioUnitario(vi.getPrecio());
                Double total = vi.getPrecio() * vi.getCantidad();
                fli.setTotal(total);
                facturaLegalItemService.save(fli);
            }

            timbradoDetalle.setNumeroActual(numeroFactura);
            timbradoDetalleService.save(timbradoDetalle);

            if (timbradoDetalle.getTimbrado() != null && Boolean.TRUE.equals(timbradoDetalle.getTimbrado().getIsElectronico())) {
                // Generar documento electrónico directamente
                try {
                    // List<FacturaLegalItem> facturaLegalItems = facturaLegalItemService.findByFacturaLegalId(facturaLegalGuardada.getId());
                    // SifenService.DocumentoElectronicoInfo infoDocumento = sifenService.generarDocumentoElectronico(facturaLegalGuardada, facturaLegalItems);

                    // // Actualizar factura con CDC
                    // facturaLegalGuardada.setCdc(infoDocumento.getCdc());
                    // // facturaLegalGuardada.setUrlQr(infoDocumento.getUrlQr());
                    // facturaLegalGuardada = facturaLegalService.save(facturaLegalGuardada);

                    // // Crear y guardar documento electrónico
                    // com.franco.dev.domain.financiero.DocumentoElectronico docElectronico = documentoElectronicoService.createFromFacturaLegal(facturaLegalGuardada);
                    // docElectronico.setCdc(infoDocumento.getCdc());
                    // docElectronico.setUrlQr(infoDocumento.getUrlQr());
                    // docElectronico.setXmlFirmado(infoDocumento.getXmlFirmado());
                    // docElectronico.setEstado(convertirStringAEstadoDE(infoDocumento.getEstadoDocumento()));
                    // docElectronico.setCodigoRespuestaSifen(infoDocumento.getCodigoRespuesta());
                    // docElectronico.setMensajeRespuestaSifen(infoDocumento.getMensajeRespuesta());
                    // docElectronico.setFechaRecepcionSifen(LocalDateTime.now());
                    // documentoElectronicoService.save(docElectronico);


                } catch (Exception e) {
                    log.error("Error al generar documento electrónico para factura ID: {}", facturaLegalGuardada.getId(), e);
                    // No lanzamos excepción para no romper el flujo, pero registramos el error
                }
            }

            return facturaLegalGuardada;
        } else {
            log.error("No se encontró un timbrado para el punto de expedición de la caja");
            return null;
        }
    }

    private EstadoDE convertirStringAEstadoDE(String estadoString) {
        if (estadoString == null) {
            return EstadoDE.PENDIENTE;
        }
        
        switch (estadoString.toUpperCase()) {
            case "PENDIENTE":
                return EstadoDE.PENDIENTE;
            case "EN_LOTE":
            case "EN_PROCESO":
                return EstadoDE.EN_LOTE;
            case "APROBADO":
                return EstadoDE.APROBADO;
            case "RECHAZADO":
                return EstadoDE.RECHAZADO;
            case "CANCELADO":
                return EstadoDE.CANCELADO;
            case "ERROR":
            default:
                return EstadoDE.PENDIENTE; // Default fallback
        }
    }

}
