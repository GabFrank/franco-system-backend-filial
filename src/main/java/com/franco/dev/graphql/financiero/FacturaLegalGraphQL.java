package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.empresarial.PuntoDeVenta;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.rabbit.dto.SaveFacturaDto;
import com.franco.dev.service.empresarial.PuntoDeVentaService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.sifen.service.SifenService;
import com.franco.dev.utilitarios.NumeroALetrasService;
import com.franco.dev.utilitarios.print.QRCodeImageGenerator;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;
import com.franco.dev.utilitarios.print.escpos.image.*;
import com.franco.dev.utilitarios.print.output.PrinterOutputStream;
import graphql.GraphQLException;
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
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.franco.dev.service.impresion.ImpresionService.shortDateTime;
import static com.franco.dev.service.utils.PrintingService.resize;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;
import static com.franco.dev.utilitarios.DateUtils.dateToStringShort;
import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class FacturaLegalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(FacturaLegalGraphQL.class);

    @Autowired
    private SifenService sifenService;

    @Autowired
    private FacturaLegalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private FacturaLegalItemGraphQL facturaLegalItemGraphQL;

    private PrintService printService;

    private PrinterOutputStream printerOutputStream;

    @Autowired
    private ImageService imageService;

    @Autowired
    private NumeroALetrasService numeroALetrasService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private PuntoDeVentaService puntoDeVentaService;

    @Autowired
    private TimbradoDetalleService timbradoDetalleService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private FacturaService facturaService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private FacturaLegalItemService facturaLegalItemService;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    @Autowired
    private CobroDetalleService cobroDetalleService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private ProductoService productoService;
    
    @Autowired
    private org.springframework.web.client.RestTemplate restTemplate;
    
    @Autowired
    private org.springframework.core.env.Environment env;

    public Optional<FacturaLegal> facturaLegal(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<FacturaLegal> facturaLegales(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Boolean deleteFacturaLegal(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countFacturaLegal() {
        return service.count();
    }

    /**
     * Guarda una factura legal con sus items y opcionalmente la imprime.
     * Este método mantiene compatibilidad con el schema GraphQL existente.
     *
     * @param entity      La factura legal a guardar
     * @param detalleList Lista de items de la factura legal
     * @param printerName Nombre de la impresora (opcional)
     * @param pdvId       ID del punto de venta (requerido)
     * @param print       Si debe imprimir la factura (opcional)
     * @return El timbrado detalle asociado
     */
    public TimbradoDetalle saveFacturaLegal(FacturaLegalInput entity, List<FacturaLegalItemInput> detalleList,
            String printerName, Integer pdvId, Boolean print) {
        try {
            if(print == null){
                print = true;
            }
            // Validar que pdvId no sea null (es requerido según el schema)
            if (pdvId == null) {
                throw new GraphQLException("pdvId es requerido");
            }

            // Crear la factura legal
            FacturaLegal facturaLegal = new FacturaLegal();

            // Mapear campos básicos
            facturaLegal.setViaTributaria(entity.getViaTributaria() != null ? entity.getViaTributaria() : false);
            facturaLegal.setCredito(entity.getCredito() != null ? entity.getCredito() : false);
            facturaLegal.setNombre(entity.getNombre());
            facturaLegal.setRuc(entity.getRuc());
            facturaLegal.setDireccion(entity.getDireccion());
            facturaLegal.setCdc(entity.getCdc());
            facturaLegal.setIvaParcial0(entity.getIvaParcial0());
            facturaLegal.setIvaParcial5(entity.getIvaParcial5());
            facturaLegal.setIvaParcial10(entity.getIvaParcial10());
            facturaLegal.setTotalParcial0(entity.getTotalParcial0());
            facturaLegal.setTotalParcial5(entity.getTotalParcial5());
            facturaLegal.setTotalParcial10(entity.getTotalParcial10());
            facturaLegal.setTotalFinal(entity.getTotalFinal());
            facturaLegal.setDescuento(entity.getDescuento());
            facturaLegal.setMonedaExtranjera(entity.getMonedaExtranjera());
            facturaLegal.setTipoCambio(entity.getTipoCambio());

            // Mapear fechas
            if (entity.getFecha() != null) {
                facturaLegal.setFecha(stringToDate(entity.getFecha()));
            } else {
                facturaLegal.setFecha(LocalDateTime.now());
            }

            // Mapear relaciones
            if (entity.getCajaId() != null) {
                // TODO: Implementar mapeo de caja si es necesario
            }

            if (entity.getClienteId() != null) {
                Optional<Cliente> cliente = clienteService.findById(entity.getClienteId());
                cliente.ifPresent(facturaLegal::setCliente);
            }

            if (entity.getVentaId() != null) {
                Optional<Venta> venta = ventaService.findById(entity.getVentaId());
                venta.ifPresent(facturaLegal::setVenta);
            }

            if (entity.getUsuarioId() != null) {
                Optional<com.franco.dev.domain.personas.Usuario> usuario = usuarioService
                        .findById(entity.getUsuarioId());
                usuario.ifPresent(facturaLegal::setUsuario);
            }

            boolean sifenHabilitado = sifenService != null && sifenService.isSifenEnabled();

            TimbradoDetalle timbradoDetalle = timbradoDetalleService
                    .getTimbradoDetalleActual(pdvId.longValue(), sifenHabilitado);

            if (timbradoDetalle == null) {
                String mensajeError = sifenHabilitado
                        ? "SIFEN está habilitado, pero no se encontró un timbrado electrónico activo para el punto de venta ID: "
                                + pdvId
                        : "SIFEN está deshabilitado, pero no se encontró un timbrado no electrónico activo para el punto de venta ID: "
                                + pdvId;
                throw new GraphQLException(mensajeError);
            }

            if (timbradoDetalle.getTimbrado() == null) {
                throw new GraphQLException(
                        "El timbrado detalle recuperado no tiene un timbrado asociado para el punto de venta ID: "
                                + pdvId);
            }

            Boolean timbradoEsElectronico = Boolean.TRUE.equals(timbradoDetalle.getTimbrado().getIsElectronico());
            if (sifenHabilitado && !timbradoEsElectronico) {
                throw new GraphQLException(
                        "SIFEN está habilitado y se requiere un timbrado electrónico activo para el punto de venta ID: "
                                + pdvId);
            }
            if (!sifenHabilitado && timbradoEsElectronico) {
                throw new GraphQLException(
                        "SIFEN está deshabilitado y se requiere un timbrado no electrónico activo para el punto de venta ID: "
                                + pdvId);
            }

            facturaLegal.setTimbradoDetalle(timbradoDetalle);
            
            // Asignar sucursal desde el timbrado detalle
            if (timbradoDetalle.getSucursal() != null && timbradoDetalle.getSucursal().getId() != null) {
                facturaLegal.setSucursalId(timbradoDetalle.getSucursal().getId());
            } else {
                throw new GraphQLException("El timbrado detalle no tiene una sucursal asignada");
            }

            // Incrementar número de factura
            Long numeroFactura = timbradoDetalle.getNumeroActual() != null ? timbradoDetalle.getNumeroActual() + 1 : 1L;
            facturaLegal.setNumeroFactura(numeroFactura.intValue());

            // Guardar la factura legal
            FacturaLegal facturaLegalGuardada = service.save(facturaLegal);

            // Actualizar dirección y email de la persona del cliente si existe cliente y persona
            if (facturaLegalGuardada.getCliente() != null && facturaLegalGuardada.getCliente().getPersona() != null) {
                Persona persona = facturaLegalGuardada.getCliente().getPersona();
                boolean necesitaActualizar = false;
                
                // Actualizar dirección si se proporciona y es diferente
                if (entity.getDireccion() != null && !entity.getDireccion().trim().isEmpty()) {
                    String nuevaDireccion = entity.getDireccion().trim();
                    String direccionActual = persona.getDireccion() != null ? persona.getDireccion() : "";
                    if (!nuevaDireccion.equals(direccionActual)) {
                        persona.setDireccion(nuevaDireccion);
                        necesitaActualizar = true;
                    }
                }
                
                // Actualizar email si se proporciona y es diferente
                if (entity.getEmail() != null && !entity.getEmail().trim().isEmpty()) {
                    String nuevoEmail = entity.getEmail().trim();
                    String emailActual = persona.getEmail() != null ? persona.getEmail() : "";
                    // Comparar sin considerar mayúsculas/minúsculas ya que PersonaService guarda en mayúsculas
                    if (!nuevoEmail.equalsIgnoreCase(emailActual)) {
                        persona.setEmail(nuevoEmail);
                        necesitaActualizar = true;
                    }
                }
                
                // Guardar persona actualizada si hubo cambios
                if (necesitaActualizar) {
                    personaService.save(persona);
                    log.info("✅ Persona del cliente actualizada - ID: {}, Dirección: {}, Email: {}", 
                        persona.getId(), persona.getDireccion(), persona.getEmail());
                }
            }

            // Guardar los items si se proporcionan
            if (detalleList != null && !detalleList.isEmpty()) {
                for (FacturaLegalItemInput itemInput : detalleList) {
                    FacturaLegalItem item = new FacturaLegalItem();
                    item.setFacturaLegal(facturaLegalGuardada);
                    item.setCantidad(itemInput.getCantidad().floatValue());
                    item.setDescripcion(itemInput.getDescripcion());
                    item.setPrecioUnitario(itemInput.getPrecioUnitario());
                    item.setTotal(itemInput.getTotal());
                    item.setIva(itemInput.getIva());
                    item.setUnidadMedida(itemInput.getUnidadMedida());
                    item.setSucursalId(facturaLegalGuardada.getSucursalId());

                    // Mapear relaciones del item
                    if (itemInput.getVentaItemId() != null) {
                        // TODO: Implementar mapeo de VentaItem si es necesario
                    }

                    // Vincular producto si se proporciona productoId
                    if (itemInput.getProductoId() != null) {
                        Optional<com.franco.dev.domain.productos.Producto> producto = productoService
                                .findById(itemInput.getProductoId());
                        producto.ifPresent(item::setProducto);
                    }

                    if (itemInput.getUsuarioId() != null) {
                        Optional<com.franco.dev.domain.personas.Usuario> usuario = usuarioService
                                .findById(itemInput.getUsuarioId());
                        usuario.ifPresent(item::setUsuario);
                    }

                    facturaLegalItemService.save(item);
                }
            }

            // Actualizar el número actual del timbrado
            timbradoDetalle.setNumeroActual(numeroFactura);
            timbradoDetalleService.save(timbradoDetalle);

            // Calcular totales de la factura antes de generar el DE
            if (detalleList != null && !detalleList.isEmpty()) {
                Double totalParcial0 = 0.0;
                Double totalParcial5 = 0.0;
                Double totalParcial10 = 0.0;
                Double ivaParcial5 = 0.0;
                Double ivaParcial10 = 0.0;
                
                for (FacturaLegalItemInput itemInput : detalleList) {
                    Double totalItem = itemInput.getTotal();
                    Integer iva = itemInput.getIva();
                    
                    // Si no se proporciona IVA en el input, intentar obtenerlo del producto
                    if (iva == null && itemInput.getProductoId() != null) {
                        Optional<com.franco.dev.domain.productos.Producto> producto = productoService
                                .findById(itemInput.getProductoId());
                        if (producto.isPresent()) {
                            iva = producto.get().getIva();
                        }
                    }
                    
                    // Default 10% si no se puede determinar el IVA
                    if (iva == null) {
                        iva = 10;
                    }
                    
                    if (iva == 10) {
                        totalParcial10 += totalItem;
                        ivaParcial10 += totalItem / 11;
                    } else if (iva == 5) {
                        totalParcial5 += totalItem;
                        ivaParcial5 += totalItem / 21;
                    } else {
                        totalParcial0 += totalItem;
                    }
                }
                
                facturaLegalGuardada.setTotalParcial0(totalParcial0);
                facturaLegalGuardada.setTotalParcial5(totalParcial5);
                facturaLegalGuardada.setTotalParcial10(totalParcial10);
                facturaLegalGuardada.setIvaParcial5(ivaParcial5);
                facturaLegalGuardada.setIvaParcial10(ivaParcial10);
                facturaLegalGuardada.setTotalFinal(totalParcial0 + totalParcial5 + totalParcial10);
                
                // Guardar factura con totales calculados
                facturaLegalGuardada = service.save(facturaLegalGuardada);
                
                log.info("✅ Totales calculados - Total Final: {}", facturaLegalGuardada.getTotalFinal());
            }

            // Generar documento electrónico si el timbrado es electrónico
            if (timbradoDetalle.getTimbrado() != null && Boolean.TRUE.equals(timbradoDetalle.getTimbrado().getIsElectronico())) {
                try {
                    // Verificar si es moneda extranjera
                    boolean esMonedaExtranjera = facturaLegalGuardada.getMonedaExtranjera() != null 
                            && !facturaLegalGuardada.getMonedaExtranjera().trim().isEmpty()
                            && facturaLegalGuardada.getTipoCambio() != null;
                    
                    if (esMonedaExtranjera) {
                        log.info("📝 Generando Documento Electrónico en moneda extranjera {} (cambio: {}) para factura ID: {}", 
                            facturaLegalGuardada.getMonedaExtranjera(), facturaLegalGuardada.getTipoCambio(), facturaLegalGuardada.getId());
                    } else {
                        log.info("📝 Generando Documento Electrónico para factura ID: {}", facturaLegalGuardada.getId());
                    }
                    
                    // El método crearDocumentoElectronico usará los campos monedaExtranjera y tipoCambio
                    // de la factura si están presentes
                    com.franco.dev.domain.financiero.DocumentoElectronico de = 
                        sifenService.crearDocumentoElectronico(facturaLegalGuardada);
                    
                    // Actualizar la factura con el CDC del DE para impresión
                    facturaLegalGuardada.setCdc(de.getCdc());
                    facturaLegalGuardada = service.save(facturaLegalGuardada);
                    
                    log.info("✅ Documento Electrónico generado exitosamente - CDC: {}", de.getCdc());
                    
                } catch (Exception e) {
                    log.error("❌ Error al generar documento electrónico para factura ID: {}", facturaLegalGuardada.getId(), e);
                    log.error("   Detalle del error: {}", e.getMessage());
                    // No lanzamos excepción para no romper el guardado de la factura
                }
            }

            // Enviar notificación si la factura es de alto valor (>= 3.000.000 Gs)
            if (facturaLegalGuardada.getTotalFinal() != null && facturaLegalGuardada.getTotalFinal() >= 3000000) {
                enviarNotificacionFacturaAltoValor(facturaLegalGuardada);
            }

            // Imprimir si se solicita
            if (print != null && print && printerName != null) {
                try {
                    log.info("🖨️  Imprimiendo factura legal ID: {} en impresora: {}", facturaLegalGuardada.getId(), printerName);
                    
                    // Verificar si es moneda extranjera
                    boolean esMonedaExtranjera = facturaLegalGuardada.getMonedaExtranjera() != null 
                            && !facturaLegalGuardada.getMonedaExtranjera().trim().isEmpty()
                            && facturaLegalGuardada.getTipoCambio() != null;
                    
                    if (esMonedaExtranjera) {
                        // Imprimir en moneda extranjera
                        List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(facturaLegalGuardada.getId());
                        printTicket58mmFacturaMonedaExtranjera(facturaLegalGuardada.getVenta(), facturaLegalGuardada, 
                                items, printerName, facturaLegalGuardada.getMonedaExtranjera(), facturaLegalGuardada.getTipoCambio());
                    } else {
                        // Imprimir normal
                        printTicket58mmFactura(facturaLegalGuardada.getVenta(), facturaLegalGuardada, null, printerName);
                    }
                    
                    log.info("✅ Factura impresa exitosamente");
                    
                } catch (Exception e) {
                    log.error("❌ Error al imprimir factura legal ID: {}", facturaLegalGuardada.getId(), e);
                    log.error("   Detalle del error: {}", e.getMessage());
                    // No lanzamos excepción para no romper el guardado
                }
            }

            return timbradoDetalle;

        } catch (Exception e) {
            log.error("Error al guardar factura legal: {}", e.getMessage(), e);
            throw new GraphQLException("Error al guardar factura legal: " + e.getMessage(), e);
        }
    }

    public FacturaDto crearFacturaDto(FacturaLegalInput facturaLegal,
            List<FacturaLegalItemInput> facturaLegalItemList) {
        FacturaDto facturaDto = new FacturaDto();
        if (facturaLegal.getCredito()) {
            facturaDto.setCredito("X");
            facturaDto.setContado("");

        } else {
            facturaDto.setContado("X");
            facturaDto.setCredito("");

        }
        if (facturaLegal.getNombre() != null) {
            if (facturaLegal.getNombre() != null)
                facturaDto.setNombre(facturaLegal.getNombre());
            if (facturaLegal.getRuc() != null)
                facturaDto.setRuc(facturaLegal.getRuc());
            if (facturaLegal.getDireccion() != null) {
                facturaDto.setDireccion(facturaLegal.getDireccion());
            } else {
                facturaDto.setDireccion("");
            }
        }
        facturaDto.setFecha(dateToStringShort(LocalDateTime.now()));
        Double totalIva10 = 0.0;
        Double totalIva5 = 0.0;
        Double totalIva = 0.0;
        Double totalFinal = 0.0;
        String totalEnLetras = "";
        List<VentaItemDto> ventaItemDtoList = new ArrayList<>();
        List<FacturaLegalItemInput> auxList = new ArrayList<>();

        if (facturaLegalItemList.size() > 8) {
            auxList = facturaLegalItemList.subList(8, facturaLegalItemList.size());
            facturaLegalItemList = facturaLegalItemList.subList(0, 8);
        }

        for (FacturaLegalItemInput fi : facturaLegalItemList) {
            if (fi.getIva() == 10) {
                totalIva10 += fi.getTotal() / 11;
            } else if (fi.getIva() == 5) {
                totalIva5 += fi.getTotal() / 21;
            }
            totalFinal += fi.getTotal();
            ventaItemDtoList.add(
                    new VentaItemDto(NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getCantidad().intValue()),
                            fi.getDescripcion(),
                            NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getPrecioUnitario().intValue()),
                            NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getTotal().intValue())));
        }

        totalIva = totalIva10 + totalIva5;
        totalEnLetras = numeroALetrasService.converter(totalFinal.intValue() + "", true);

        facturaDto.setTotalParcial(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal.intValue()));
        facturaDto.setTotal(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal.intValue()));
        facturaDto.setIvaParcial0(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva10.intValue()));
        facturaDto.setIvaParcial5(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva5.intValue()));
        facturaDto.setIvaFinal(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva.intValue()));
        facturaDto.setTotalEnLetras(totalEnLetras);
        facturaDto.setFacturaLegalItemInputList(facturaLegalItemList);
        facturaDto.setFacturaLegalInput(facturaLegal);
        // facturaDto.setVenta(ventaService.findById(facturaLegal.getVentaId()));
        return facturaDto;
    }

    public void generarFactura(FacturaLegalInput facturaLegal, List<FacturaLegalItemInput> facturaLegalItemList,
            String printerName) {
        try {
            FacturaDto facturaDto = new FacturaDto();
            if (facturaLegal.getCredito()) {
                facturaDto.setCredito("X");
                facturaDto.setContado("");

            } else {
                facturaDto.setContado("X");
                facturaDto.setCredito("");

            }
            if (facturaLegal.getNombre() != null) {
                if (facturaLegal.getNombre() != null)
                    facturaDto.setNombre(facturaLegal.getNombre());
                if (facturaLegal.getRuc() != null)
                    facturaDto.setRuc(facturaLegal.getRuc());
                if (facturaLegal.getDireccion() != null) {
                    facturaDto.setDireccion(facturaLegal.getDireccion());
                } else {
                    facturaDto.setDireccion("");
                }
            }
            facturaDto.setFecha(dateToStringShort(LocalDateTime.now()));
            Double totalIva10 = 0.0;
            Double totalIva5 = 0.0;
            Double totalIva = 0.0;
            Double totalFinal = 0.0;
            String totalEnLetras = "";
            List<VentaItemDto> ventaItemDtoList = new ArrayList<>();
            List<FacturaLegalItemInput> auxList = new ArrayList<>();

            if (facturaLegalItemList.size() > 8) {
                auxList = facturaLegalItemList.subList(8, facturaLegalItemList.size());
                facturaLegalItemList = facturaLegalItemList.subList(0, 8);
            }

            for (FacturaLegalItemInput fi : facturaLegalItemList) {
                if (fi.getIva() == 10) {
                    totalIva10 += fi.getTotal() / 11;
                } else if (fi.getIva() == 5) {
                    totalIva5 += fi.getTotal() / 21;
                }
                totalFinal += fi.getTotal();
                ventaItemDtoList.add(new VentaItemDto(
                        NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getCantidad().intValue()),
                        fi.getDescripcion(),
                        NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getPrecioUnitario().intValue()),
                        NumberFormat.getNumberInstance(Locale.GERMAN).format(fi.getTotal().intValue())));
            }

            totalIva = totalIva10 + totalIva5;
            totalEnLetras = numeroALetrasService.converter(totalFinal.intValue() + "", true);

            facturaDto.setTotalParcial(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal.intValue()));
            facturaDto.setTotal(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal.intValue()));
            facturaDto.setIvaParcial0(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva10.intValue()));
            facturaDto.setIvaParcial5(NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva5.intValue()));
            facturaDto.setIvaFinal(
                    NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva10.intValue() + totalIva5.intValue()));
            facturaDto.setTotalEnLetras(totalEnLetras);

            File file = ResourceUtils.getFile("classpath:factura.jrxml");
            JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(ventaItemDtoList);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("contado", facturaDto.getContado());
            parameters.put("credito", facturaDto.getCredito());
            parameters.put("fecha", facturaDto.getFecha());
            parameters.put("ivaTotal10", facturaDto.getIvaParcial10());
            parameters.put("ivaTotal5", facturaDto.getIvaParcial5());
            parameters.put("ivaTotal", facturaDto.getIvaFinal());
            parameters.put("nombre", facturaDto.getNombre());
            parameters.put("ruc", facturaDto.getRuc());
            parameters.put("totalFinal", facturaDto.getTotal());
            parameters.put("totalEnLetras", facturaDto.getTotalEnLetras());
            parameters.put("direccion", facturaDto.getDireccion());

            JasperPrint jasperPrint1 = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            jasperPrint1.setPageHeight(842);

            File file2 = ResourceUtils.getFile("classpath:factura2.jrxml");
            JasperReport jasperReport2 = JasperCompileManager.compileReport(file.getAbsolutePath());
            JRBeanCollectionDataSource dataSource2 = new JRBeanCollectionDataSource(ventaItemDtoList);
            Map<String, Object> parameters2 = new HashMap<>();
            parameters2.put("contado", facturaDto.getContado());
            parameters2.put("credito", facturaDto.getCredito());
            parameters2.put("fecha", facturaDto.getFecha());
            parameters2.put("ivaTotal", facturaDto.getIvaFinal());
            parameters2.put("nombre", facturaDto.getNombre());
            parameters2.put("ruc", facturaDto.getRuc());
            parameters2.put("totalFinal", facturaDto.getTotal());
            parameters2.put("totalEnLetras", facturaDto.getTotalEnLetras());
            parameters2.put("direccion", facturaDto.getDireccion());

            JasperPrint jasperPrint2 = JasperFillManager.fillReport(jasperReport2, parameters2, dataSource2);

            JRPrintPage page2 = jasperPrint2.getPages().get(0);
            List<JRPrintElement> elements = page2.getElements();

            for (JRPrintElement e : elements) {
                e.setY(e.getY() + 403);
                jasperPrint1.getPages().get(0).addElement(e);
            }

            // OutputStream output;
            // output = new FileOutputStream(new
            // File("/Users/gabfranck/Desktop/prueba.pdf"));
            // JasperExportManager.exportReportToPdfStream(jasperPrint1, output);

            printFactura(jasperPrint1, printerName);
            if (auxList.size() > 0)
                generarFactura(facturaLegal, auxList, printerName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printFactura(JasperPrint jasperPrint, String printerName) throws GraphQLException {
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

        printService = PrinterOutputStream.getPrintServiceByName(printerName);

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

    public void generarFacturaAutoImpreso(Venta venta, Cobro cobro, List<VentaItem> ventaItemList,
            List<CobroDetalleInput> cobroDetalleList, Boolean reimpresion, String printerName, String local,
            FacturaLegalInput facturaLegal) throws Exception {
        Sucursal sucursal = null;
        PrintService selectedPrintService = null;
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService printer : printServices) {
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

        if (selectedPrintService != null)
            printerOutputStream = new PrinterOutputStream(selectedPrintService);

        // creating the EscPosImage, need buffered image and algorithm.
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        // Styles
        Style center = new Style().setJustification(EscPosConst.Justification.Center);

        BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
        imageBufferedImage = resize(imageBufferedImage, 200, 100);
        BitImageWrapper imageWrapper = new BitImageWrapper();
        EscPos escpos = null;
        escpos = new EscPos(printerOutputStream);
        Bitonal algorithm = new BitonalThreshold();
        EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
        imageWrapper.setJustification(EscPosConst.Justification.Center);
        escpos.feed(5);
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
            // log.info(vi.getProducto().getDescripcion());
            escpos.writeLF(vi.getProducto().getDescripcion());
            escpos.write(new Style().setBold(true), cantidad);
            String valorUnitario = NumberFormat.getNumberInstance(Locale.GERMAN)
                    .format(vi.getPrecioVenta().getPrecio().intValue());
            String valorTotal = String
                    .valueOf(vi.getPrecioVenta().getPrecio().intValue() * vi.getCantidad().intValue());
            for (int i = 10; i > cantidad.length(); i--) {
                escpos.write(" ");
            }
            escpos.write(valorUnitario);
            for (int i = 20 - valorUnitario.length(); i > valorTotal.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(NumberFormat.getNumberInstance(Locale.GERMAN)
                    .format(vi.getPrecioVenta().getPrecio().intValue() * vi.getCantidad().intValue()));
        }
        escpos.writeLF("--------------------------------");
        String valorGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(venta.getTotalGs().intValue());
        for (int i = 22; i > valorGs.length(); i--) {
            escpos.write(" ");
        }
        escpos.writeLF(valorGs);
        // log.info(valorGs);
        escpos.write("Total Rs: ");
        String valorRs = String.format("%.2f", venta.getTotalRs());
        for (int i = 22; i > valorGs.length(); i--) {
            escpos.write(" ");
        }
        escpos.writeLF(valorRs);
        escpos.write("Total Ds: ");
        // String valorDs = NumberFormat.getNumberInstance(new Locale("sk",
        // "SK")).format(venta.getTotalDs());
        String valorDs = String.format("%.2f", venta.getTotalDs());
        for (int i = 22; i > valorGs.length(); i--) {
            escpos.write(" ");
        }
        escpos.writeLF(valorDs);
        if (sucursal != null && sucursal.getNroDelivery() != null) {
            escpos.write(center, "Delivery? Escaneá el código qr o escribinos al ");
            escpos.writeLF(center, sucursal.getNroDelivery());
        }
        // escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center),
        // "wa.me/595986128000");
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

    public Boolean imprimirFacturasPorCaja(Long id, String printerName, Long sucId) {
        Boolean ok = false;
        List<FacturaLegal> facturaLegalList = service.findByCajaId(id);
        Integer count = 0;
        for (FacturaLegal fl : facturaLegalList) {
            count++;
            List<FacturaLegalItem> facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(fl.getId());
            List<FacturaLegalItemInput> facturaLegalItemInputList = new ArrayList<>();
            ModelMapper m = new ModelMapper();
            FacturaLegalInput input = m.map(fl, FacturaLegalInput.class);
            input.setClienteId(fl.getCliente().getId());
            if (fl.getCaja() != null)
                input.setCajaId(fl.getCaja().getId());
            if (fl.getVenta() != null)
                input.setVentaId(fl.getVenta().getId());
            input.setUsuarioId(fl.getUsuario().getId());
            input.setTimbradoDetalleId(fl.getTimbradoDetalle().getId());
            for (FacturaLegalItem flItem : facturaLegalItemList) {
                ModelMapper i = new ModelMapper();
                FacturaLegalItemInput itemInput = i.map(flItem, FacturaLegalItemInput.class);
                if (flItem.getUsuario() != null)
                    itemInput.setUsuarioId(flItem.getUsuario().getId());
                if (flItem.getVentaItem() != null)
                    itemInput.setVentaItemId(flItem.getVentaItem().getId());
                if (flItem.getFacturaLegal() != null)
                    itemInput.setFacturaLegalId(flItem.getFacturaLegal().getId());
                facturaLegalItemInputList.add(itemInput);
            }
            Boolean continuar = true;
            if (facturaLegalList.size() == count) {
                continuar = false;
            }
            try {
                generarFacturaAutoImpreso(fl.getVenta() != null ? fl.getVenta() : null, null,
                        new ArrayList<>(), new ArrayList<>(), continuar, printerName,
                        fl.getTimbradoDetalle().getPuntoDeVenta().getNombre(), input);
                fl.setViaTributaria(true);
                // propagacionService.propagarEntidad(fl, TipoEntidad.FACTURA);
                ok = true;
            } catch (Exception e) {
                log.error("Error al imprimir factura legal ID: {}", fl.getId(), e);
            }
        }
        return ok;
    }

    public Boolean reimprimirFacturaLegal(Long id, Long sucId, String printerName) {
        FacturaLegal facturaLegal = service.findById(id).orElse(null);
        List<FacturaLegalItem> facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(id);
        try {
            // Verificar si es moneda extranjera
            boolean esMonedaExtranjera = facturaLegal.getMonedaExtranjera() != null 
                    && !facturaLegal.getMonedaExtranjera().trim().isEmpty()
                    && facturaLegal.getTipoCambio() != null;
            
            if (esMonedaExtranjera) {
                printTicket58mmFacturaMonedaExtranjera(facturaLegal.getVenta(), facturaLegal, 
                        facturaLegalItemList, printerName, facturaLegal.getMonedaExtranjera(), facturaLegal.getTipoCambio());
            } else {
                printTicket58mmFactura(facturaLegal.getVenta(), facturaLegal, facturaLegalItemList, printerName);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void printTicket58mmFactura(Venta venta, FacturaLegal facturaLegal,
            List<FacturaLegalItem> facturaLegalItemList, String printerName) throws Exception {

        if (facturaLegalItemList == null) {
            facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(facturaLegal.getId());
        }

        printService = PrinterOutputStream.getPrintServiceByName(printerName);
        Sucursal sucursal = sucursalService.findById(facturaLegal.getSucursalId()).orElse(null);
        Delivery delivery = null;
        if (venta != null)
            delivery = venta.getDelivery();
        Double descuento = facturaLegal.getDescuento() != null ? facturaLegal.getDescuento() : 0.0;
        Double aumento = 0.0; // Los aumentos se manejan como descuentos negativos
        Double vueltoGs = 0.0;
        Double vueltoRs = 0.0;
        Double vueltoDs = 0.0;
        Double pagadoGs = 0.0;
        Double pagadoRs = 0.0;
        Double pagadoDs = 0.0;
        // Usar los datos ya calculados en FacturaLegal
        Double totalFinal = facturaLegal.getTotalFinal();
        Double totalIva10 = facturaLegal.getIvaParcial10();
        Double totalIva5 = facturaLegal.getIvaParcial5();
        Double totalIva = totalIva10 + totalIva5;
        Double precioDeliveryGs = 0.0;
        Double precioDeliveryRs = 0.0;
        Double precioDeliveryDs = 0.0;
        Double cambioRs = cambioService.findLastByMonedaId(Long.valueOf(2)).getValorEnGs();
        Double cambioDs = cambioService.findLastByMonedaId(Long.valueOf(3)).getValorEnGs();
        
        // Obtener instancias de las monedas para usar getAbreviatura()
        Moneda monedaGs = monedaService.findById(Long.valueOf(1)).orElse(null);
        Moneda monedaRs = monedaService.findById(Long.valueOf(2)).orElse(null);
        Moneda monedaDs = monedaService.findById(Long.valueOf(3)).orElse(null);

        if (delivery != null) {
            precioDeliveryGs = delivery.getPrecio().getValor();
            precioDeliveryRs = precioDeliveryGs / cambioRs;
            precioDeliveryDs = precioDeliveryGs / cambioDs;
        }

        if (printService != null) {
            printerOutputStream = this.printerOutputStream != null ? this.printerOutputStream
                    : new PrinterOutputStream(printService);
            // creating the EscPosImage, need buffered image and algorithm.
            // Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center)
                    .setFontSize(Style.FontSize._1, Style.FontSize._1);

            BufferedImage imageBufferedImage = ImageIO.read(new File(imageService.storageDirectoryPath + "logo.png"));
            imageBufferedImage = resize(imageBufferedImage, 200, 100);
            BitImageWrapper imageWrapper = new BitImageWrapper();
            EscPos escpos = new EscPos(printerOutputStream);
            Bitonal algorithm = new BitonalThreshold();
            EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(imageBufferedImage), algorithm);
            imageWrapper.setJustification(EscPosConst.Justification.Center);
            escpos.writeLF("--------------------------------");
            escpos.write(imageWrapper, escposImage);
            escpos.writeLF(factura, facturaLegal.getTimbradoDetalle().getTimbrado().getRazonSocial().toUpperCase());
            escpos.writeLF(factura, "RUC: " + facturaLegal.getTimbradoDetalle().getTimbrado().getRuc());
            escpos.writeLF(factura, "Timbrado: " + facturaLegal.getTimbradoDetalle().getTimbrado().getNumero());

            // Si el timbrado es electronico, no se imprime la fecha de inicio y fin
            if (facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico() != true) {
                escpos.writeLF(factura, "De "
                        + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaInicio()
                                .format(impresionService.shortDate)
                        + " a "
                        + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaFin()
                                .format(impresionService.shortDate));
            }

            Long numeroFacturaAux = Long.valueOf(facturaLegal.getNumeroFactura());
            StringBuilder numeroFacturaString = new StringBuilder();
            for (int i = 7; i > numeroFacturaAux.toString().length(); i--) {
                numeroFacturaString.append("0");
            }
            if (facturaLegal.getNumeroFactura() != null) {
                numeroFacturaString.append(facturaLegal.getNumeroFactura());
            } else {
                numeroFacturaString.append(numeroFacturaAux.toString());
            }
            escpos.writeLF(factura, "Nro: " + sucursal.getCodigoEstablecimientoFactura() + "-"
                    + facturaLegal.getTimbradoDetalle().getPuntoExpedicion() + "-" + numeroFacturaString.toString());
            escpos.writeLF(center, "Condicion: " + (facturaLegal.getCredito() == false ? "Contado" : "Crédito"));

            if (sucursal != null) {
                escpos.writeLF(center, "Suc: " + sucursal.getNombre());
                if (sucursal.getCiudad() != null) {
                    escpos.writeLF(center, sucursal.getCiudad().getDescripcion());
                    if (sucursal.getDireccion() != null) {
                        escpos.writeLF(center, sucursal.getNombre() + " - " + sucursal.getDireccion());
                    }
                }
            }
            if (venta != null)
                escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());
            if (delivery != null) {
                escpos.writeLF(center, "Modo: Delivery");
            }
            if (venta != null && venta.getUsuario() != null) {
                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre());
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

            if (facturaLegal.getRuc() != null) {
                if (!facturaLegal.getRuc().contains("-")) {
                    facturaLegal.setRuc(facturaLegal.getRuc() + getDigitoVerificadorString(facturaLegal.getRuc()));
                }
                ;
            }

            escpos.writeLF("CI/RUC: " + facturaLegal.getRuc());
            if (facturaLegal.getDireccion() != null)
                escpos.writeLF("Dir: " + facturaLegal.getDireccion());

            escpos.writeLF("--------------------------------");

            escpos.writeLF("Producto");
            escpos.writeLF("Cant  IVA   P.U              P.T");
            escpos.writeLF("--------------------------------");
            for (FacturaLegalItem vi : facturaLegalItemList) {
                // Prioridad 1: IVA del item directamente
                Integer iva = vi.getIva();
                
                // Prioridad 2: IVA del producto vinculado directamente
                if (iva == null && vi.getProducto() != null) {
                    iva = vi.getProducto().getIva();
                }
                // Prioridad 3: IVA del producto a través de la presentación
                else if (iva == null && vi.getPresentacion() != null) {
                    iva = vi.getPresentacion().getProducto().getIva();
                }
                
                // Default 10% si no se puede determinar el IVA
                if (iva == null) {
                    iva = 10;
                }
                
                // Construir string de cantidad con unidad de medida si está disponible
                String cantidadStr;
                if (vi.getUnidadMedida() != null && !vi.getUnidadMedida().trim().isEmpty()) {
                    cantidadStr = vi.getCantidad().intValue() + " " + vi.getUnidadMedida() + " (" + vi.getCantidad() + ") " + iva + "%";
                } else {
                    cantidadStr = vi.getCantidad().intValue() + " (" + vi.getCantidad() + ") " + iva + "%";
                }
                
                escpos.writeLF(vi.getDescripcion());
                escpos.write(new Style().setBold(true), cantidadStr);
                String valorUnitario = NumberFormat.getNumberInstance(Locale.GERMAN)
                        .format(vi.getPrecioUnitario().intValue());
                String valorTotal = NumberFormat.getNumberInstance(Locale.GERMAN).format(vi.getTotal().intValue());
                for (int i = 14; i > cantidadStr.length(); i--) {
                    escpos.write(" ");
                }
                escpos.write(valorUnitario);
                for (int i = 16 - valorUnitario.length(); i > valorTotal.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTotal);
            }
            // escpos.writeLF("--------------------------------");

            // Sección de totales comentada - ahora se muestra por moneda abajo
            // // Mostrar desglose de descuento si existe
            // if (descuento > 0) {
            //     Double totalSinDescuento = totalFinal + descuento;
            //     escpos.write("Total parcial: ");
            //     String totalParcialGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalSinDescuento);
            //     for (int i = 17; i > totalParcialGs.length(); i--) {
            //         escpos.write(" ");
            //     }
            //     escpos.writeLF(totalParcialGs);
            //
            //     escpos.write("Descuento: ");
            //     String descuentoGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(descuento);
            //     for (int i = 21; i > descuentoGs.length(); i--) {
            //         escpos.write(" ");
            //     }
            //     escpos.writeLF(descuentoGs);
            // }
            //
            // escpos.write("Total Gs: ");
            // String valorGs = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal);
            // for (int i = 22; i > valorGs.length(); i--) {
            //     escpos.write(" ");
            // }
            // escpos.writeLF(new Style().setBold(true), valorGs);

            // Nueva sección de totales por moneda
            escpos.writeLF("------------Totales-------------");
            // Header: 4 (moneda) + 9 (Parcial) + 9 (Desc.) + 10 (Final) = 32
            escpos.write("   "); // 4 espacios para moneda
            escpos.write("   Parcial"); // 7 chars
            escpos.write("    "); // 2 espacios = 9 total
            escpos.write("Desc."); // 5 chars
            escpos.write("     "); // 4 espacios = 9 total
            escpos.writeLF("Final"); // 5 chars
            
            // Calcular totales por moneda
            Double totalParcialGs = totalFinal + descuento;
            Double totalParcialRs = totalParcialGs / cambioRs;
            Double totalParcialDs = totalParcialGs / cambioDs;
            
            Double descuentoRs = descuento / cambioRs;
            Double descuentoDs = descuento / cambioDs;
            
            Double totalFinalRs = totalFinal / cambioRs;
            Double totalFinalDs = totalFinal / cambioDs;
            
            // Línea de Guaraníes
            escpos.write(monedaGs != null ? monedaGs.getAbreviatura() + ". " : "Gs. ");
            String parcialGsStr = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalParcialGs.intValue());
            int espaciosParcialGs = 9 - parcialGsStr.length();
            for (int i = 0; i < espaciosParcialGs; i++) {
                escpos.write(" ");
            }
            escpos.write(parcialGsStr);
            
            String descGsStr = NumberFormat.getNumberInstance(Locale.GERMAN).format(descuento.intValue());
            int espaciosDescGs = 9 - descGsStr.length();
            for (int i = 0; i < espaciosDescGs; i++) {
                escpos.write(" ");
            }
            escpos.write(descGsStr);
            
            String finalGsStr = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalFinal.intValue());
            int espaciosFinalGs = 10 - finalGsStr.length();
            for (int i = 0; i < espaciosFinalGs; i++) {
                escpos.write(" ");
            }
            escpos.writeLF(finalGsStr);
            
            // Línea de Reales
            escpos.write(monedaRs != null ? monedaRs.getAbreviatura() + ". " : "Rs. ");
            String parcialRsStr = String.format(Locale.GERMAN, "%.2f", totalParcialRs);
            int espaciosParcialRs = 9 - parcialRsStr.length();
            for (int i = 0; i < espaciosParcialRs; i++) {
                escpos.write(" ");
            }
            escpos.write(parcialRsStr);
            
            String descRsStr = String.format(Locale.GERMAN, "%.2f", descuentoRs);
            int espaciosDescRs = 9 - descRsStr.length();
            for (int i = 0; i < espaciosDescRs; i++) {
                escpos.write(" ");
            }
            escpos.write(descRsStr);
            
            String finalRsStr = String.format(Locale.GERMAN, "%.2f", totalFinalRs);
            int espaciosFinalRs = 10 - finalRsStr.length();
            for (int i = 0; i < espaciosFinalRs; i++) {
                escpos.write(" ");
            }
            escpos.writeLF(finalRsStr);
            
            // Línea de Dólares
            escpos.write(monedaDs != null ? monedaDs.getAbreviatura() + ". " : "Us. ");
            String parcialDsStr = String.format(Locale.GERMAN, "%.2f", totalParcialDs);
            int espaciosParcialDs = 9 - parcialDsStr.length();
            for (int i = 0; i < espaciosParcialDs; i++) {
                escpos.write(" ");
            }
            escpos.write(parcialDsStr);
            
            String descDsStr = String.format(Locale.GERMAN, "%.2f", descuentoDs);
            int espaciosDescDs = 9 - descDsStr.length();
            for (int i = 0; i < espaciosDescDs; i++) {
                escpos.write(" ");
            }
            escpos.write(descDsStr);
            
            String finalDsStr = String.format(Locale.GERMAN, "%.2f", totalFinalDs);
            int espaciosFinalDs = 10 - finalDsStr.length();
            for (int i = 0; i < espaciosFinalDs; i++) {
                escpos.write(" ");
            }
            escpos.writeLF(finalDsStr);

            // agregar cobro detalle por moneda
            if (venta != null && venta.getCobro() != null) {
                List<CobroDetalle> cobroDetalleList = cobroDetalleService.findByCobroId(venta.getCobro().getId());
                
                // Filtrar solo pagos y vueltos, ignorar descuentos y aumentos
                List<CobroDetalle> pagosYVueltos = new ArrayList<>();
                for (CobroDetalle cd : cobroDetalleList) {
                    if ((cd.getPago() != null && cd.getPago()) || (cd.getVuelto() != null && cd.getVuelto())) {
                        pagosYVueltos.add(cd);
                    }
                }
                
                if (!pagosYVueltos.isEmpty()) {
                    // Agrupar por moneda
                    Map<Long, List<CobroDetalle>> porMoneda = new LinkedHashMap<>();
                    for (CobroDetalle cd : pagosYVueltos) {
                        Long monedaId = cd.getMoneda().getId();
                        if (!porMoneda.containsKey(monedaId)) {
                            porMoneda.put(monedaId, new ArrayList<>());
                        }
                        porMoneda.get(monedaId).add(cd);
                    }
                    
                    escpos.writeLF("------------Detalles------------");
                    // Header con columnas Pago y Vuelto (total 32 caracteres)
                    // Distribución: 4 (moneda) + 18 (pago) + 10 (vuelto) = 32
                    escpos.write("    ");  // espacios para columna moneda (4 chars)
                    escpos.write("          Pago");  // (4 chars)
                    for (int i = 0; i < 8; i++) {  // espacios para completar columna pago (10 chars)
                        escpos.write(" ");
                    }
                    escpos.writeLF("Vuelto");  // (6 chars + los que quedan = 14 chars totales para título vuelto)
                    
                    // Procesar cada moneda
                    for (Map.Entry<Long, List<CobroDetalle>> entry : porMoneda.entrySet()) {
                        List<CobroDetalle> detallesMoneda = entry.getValue();
                        
                        // Agrupar pagos por forma de pago para esta moneda
                        Map<Long, CobroDetalle> pagosPorFormaPago = new LinkedHashMap<>();
                        Double vueltoTotal = 0.0;
                        
                        for (CobroDetalle cd : detallesMoneda) {
                            if (cd.getPago() != null && cd.getPago()) {
                                Long formaPagoId = cd.getFormaPago() != null ? cd.getFormaPago().getId() : 0L;
                                pagosPorFormaPago.put(formaPagoId, cd);
                            } else if (cd.getVuelto() != null && cd.getVuelto()) {
                                vueltoTotal += cd.getValor();
                            }
                        }
                        
                        // Imprimir cada pago
                        // Distribución de 32 caracteres: 4 (moneda) + 18 (pago) + 10 (vuelto)
                        int lineasPago = 0;
                        for (Map.Entry<Long, CobroDetalle> pagoEntry : pagosPorFormaPago.entrySet()) {
                            CobroDetalle pago = pagoEntry.getValue();
                            String simboloMoneda = pago.getMoneda() != null ? pago.getMoneda().getAbreviatura() + "." : "N/A";
                            
                            // Obtener abreviatura de forma de pago
                            String abrevFormaPago = pago.getFormaPago() != null ? pago.getFormaPago().getAbreviatura() : "N/A";
                            
                            // Formatear valor del pago
                            String valorPagoStr;
                            if (pago.getMoneda().getId() == 1) { // Guaraníes
                                valorPagoStr = NumberFormat.getNumberInstance(Locale.GERMAN).format(pago.getValor().intValue());
                            } else { // Otras monedas (con decimales)
                                valorPagoStr = String.format(Locale.GERMAN, "%.2f", pago.getValor());
                            }
                            valorPagoStr = valorPagoStr + " (" + abrevFormaPago + ")";
                            
                            // Columna 1: Moneda (4 caracteres)
                            escpos.write(simboloMoneda);
                            for (int i = simboloMoneda.length(); i < 4; i++) {
                                escpos.write(" ");
                            }
                            
                            // Columna 2: Pago (18 caracteres, alineado a derecha)
                            int espaciosPago = 18 - valorPagoStr.length();
                            for (int i = 0; i < espaciosPago; i++) {
                                escpos.write(" ");
                            }
                            escpos.write(valorPagoStr);
                            
                            // Columna 3: Vuelto (10 caracteres, alineado a derecha)
                            if (lineasPago == 0) {
                                String valorVueltoStr;
                                if (vueltoTotal != 0) {
                                    // Usar valor absoluto del vuelto (puede venir negativo de la BD)
                                    Double vueltoAbsoluto = Math.abs(vueltoTotal);
                                    if (pago.getMoneda().getId() == 1) { // Guaraníes
                                        valorVueltoStr = NumberFormat.getNumberInstance(Locale.GERMAN).format(vueltoAbsoluto.intValue());
                                    } else { // Otras monedas (con decimales)
                                        valorVueltoStr = String.format(Locale.GERMAN, "%.2f", vueltoAbsoluto);
                                    }
                                } else {
                                    valorVueltoStr = "0";
                                }
                                int espaciosVuelto = 10 - valorVueltoStr.length();
                                for (int i = 0; i < espaciosVuelto; i++) {
                                    escpos.write(" ");
                                }
                                escpos.writeLF(valorVueltoStr);
                            } else {
                                // Para líneas subsiguientes, dejar vacío
                                escpos.writeLF("");
                            }
                            
                            lineasPago++;
                        }
                        
                        // Si solo hay vuelto sin pago (caso raro pero por si acaso)
                        if (pagosPorFormaPago.isEmpty() && vueltoTotal != 0) {
                            CobroDetalle primerDetalle = detallesMoneda.get(0);
                            String simboloMoneda = primerDetalle.getMoneda() != null ? primerDetalle.getMoneda().getAbreviatura() + "." : "N/A";
                            
                            // Columna 1: Moneda (4 caracteres)
                            escpos.write(simboloMoneda);
                            for (int i = simboloMoneda.length(); i < 4; i++) {
                                escpos.write(" ");
                            }
                            
                            // Columna 2: Pago vacía (18 caracteres)
                            for (int i = 0; i < 18; i++) {
                                escpos.write(" ");
                            }
                            
                            // Columna 3: Vuelto (10 caracteres, alineado a derecha)
                            // Usar valor absoluto del vuelto (puede venir negativo de la BD)
                            Double vueltoAbsoluto = Math.abs(vueltoTotal);
                            String valorVueltoStr;
                            if (primerDetalle.getMoneda().getId() == 1) {
                                valorVueltoStr = NumberFormat.getNumberInstance(Locale.GERMAN).format(vueltoAbsoluto.intValue());
                            } else {
                                valorVueltoStr = String.format(Locale.GERMAN, "%.2f", vueltoAbsoluto);
                            }
                            int espaciosVuelto = 10 - valorVueltoStr.length();
                            for (int i = 0; i < espaciosVuelto; i++) {
                                escpos.write(" ");
                            }
                            escpos.writeLF(valorVueltoStr);
                        }
                    }
                }
            }

            escpos.writeLF("--------Liquidacion IVA---------");
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
            String totalIva0S = NumberFormat.getNumberInstance(Locale.GERMAN)
                    .format(facturaLegal.getTotalParcial0().intValue());
            for (int i = 19; i > totalIva0S.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva0S);
            String totalFinalIvaS = NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIva.intValue());
            escpos.write("Total IVA:   ");
            for (int i = 19; i > totalFinalIvaS.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalFinalIvaS);
            // escpos.writeLF("--------Liquidación IVA---------");
            // escpos.write("Gravadas 10%:");
            // Double totalIvaFinal = totalIva10 + totalIva5;
            // String totalIvaFinalS =
            // NumberFormat.getNumberInstance(Locale.GERMAN).format(totalIvaFinal.intValue());
            // for (int i = 19; i > totalIvaFinalS.length(); i--) {
            // escpos.write(" ");
            // }
            // escpos.writeLF(iva10s);
            // escpos.write("Gravadas 5%: ");
            // for (int i = 19; i > 1; i--) {
            // escpos.write(" ");
            // }
            // escpos.writeLF("0");

            escpos.writeLF("--------------------------------");
            // vamos a reemplazar este sector por el codigo cdc
            // if (sucursal != null && sucursal.getNroDelivery() != null) {
            // escpos.write(center, "Delivery? Escaneá el código qr o escribinos al ");
            // escpos.writeLF(center, sucursal.getNroDelivery());
            // }
            // if (sucursal.getNroDelivery() != null) {
            // escpos.write(qrCode.setSize(5).setJustification(EscPosConst.Justification.Center),
            // "wa.me/" + sucursal.getNroDelivery());
            // }
            // Generar CDC directamente usando la función generarCdc
            if (facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico() != null
                    && facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico()) {

                DocumentoElectronico documentoElectronico = documentoElectronicoService
                        .findByFacturaLegalIdAndSucursalId(facturaLegal.getId(), facturaLegal.getSucursalId());

                String cdc = documentoElectronico.getCdc();

                // Generar URL del QR para SIFEN
                String urlQr = documentoElectronico.getUrlQr();

                // Imprimir QR como imagen generada por ZXing
                if (urlQr != null) {
                    try {
                        log.info("Generando imagen de QR para URL de {} caracteres.", urlQr.length());
                        BufferedImage qrImage = QRCodeImageGenerator.generateQRCodeImage(urlQr, 250, 250);

                        // Preparar la imagen para la impresión ESC/POS, reutilizando las variables
                        // existentes
                        imageWrapper.setJustification(EscPosConst.Justification.Center);
                        escposImage = new EscPosImage(new CoffeeImageImpl(qrImage), algorithm);

                        // Enviar la imagen a la impresora
                        escpos.write(imageWrapper, escposImage);
                        escpos.feed(1);
                        log.info("Imagen de QR enviada a la impresora exitosamente.");

                    } catch (Exception e) {
                        log.error("No se pudo generar o imprimir la imagen del código QR. URL: {}", urlQr, e);
                        escpos.writeLF(center, "ERROR: No se pudo generar el código QR.");
                        escpos.writeLF(center, "URL de consulta:");
                        escpos.writeLF(center, urlQr);
                        escpos.feed(1);
                    }
                }

                // Texto requerido por SIFEN debajo del QR
                escpos.writeLF(center,
                        "Consulte la validez de esta Factura Electronica con el numero de CDC impreso abajo en:");
                escpos.writeLF(center, "https://ekuatia.set.gov.py/consultas");

                // Formatear CDC en grupos de 4 dígitos
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
            }
            escpos.feed(1);
            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
            // escpos.writeLF("--------------------------------");
            // escpos.write( "Conservar este papel ");
            escpos.feed(5);

            try {
                if (true) {
                    escpos.close();
                    printerOutputStream.close();
                    this.printerOutputStream = null;
                } else {
                    this.printerOutputStream = printerOutputStream;
                }
                // if (facturaLegal.getId() == null) {
                // Long numero = timbradoDetalleService.aumentarNumeroFactura(timbradoDetalle);
                // facturaLegal.setTimbradoDetalleId(timbradoDetalle.getId());
                // if(venta!=null){
                // facturaLegal.setVentaId(venta.getId());
                // facturaLegal.setFecha(venta.getCreadoEn());
                // facturaLegal.setClienteId(venta.getCliente().getId());
                // facturaLegal.setCajaId(venta.getCaja().getId());
                // }
                // facturaLegal.setTotalFinal(totalFinal);
                // facturaLegal.setIvaParcial5(totalIva5);
                // facturaLegal.setIvaParcial10(totalIva10);
                // facturaLegal.setViaTributaria(false);
                // facturaLegal.setAutoimpreso(true);
                // facturaLegal.setNumeroFactura(numero.intValue());
                // facturaLegal.setTotalParcial5(ventaIva5);
                // facturaLegal.setTotalParcial10(ventaIva10);
                // facturaLegal.setTotalParcial0(ventaIva0);
                // }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    /**
     * Imprime un ticket de factura de 58mm en moneda extranjera.
     * Todos los valores se muestran convertidos a la moneda extranjera seleccionada.
     * 
     * @param venta La venta asociada (opcional)
     * @param facturaLegal La factura legal a imprimir
     * @param facturaLegalItemList Lista de items de la factura
     * @param printerName Nombre de la impresora
     * @param monedaExtranjera Código de moneda extranjera (ej: "USD", "EUR")
     * @param tipoCambio Tipo de cambio utilizado
     */
    public void printTicket58mmFacturaMonedaExtranjera(Venta venta, FacturaLegal facturaLegal,
            List<FacturaLegalItem> facturaLegalItemList, String printerName, String monedaExtranjera, Double tipoCambio) throws Exception {

        if (facturaLegalItemList == null) {
            facturaLegalItemList = facturaLegalItemService.findByFacturaLegalId(facturaLegal.getId());
        }

        printService = PrinterOutputStream.getPrintServiceByName(printerName);
        Sucursal sucursal = sucursalService.findById(facturaLegal.getSucursalId()).orElse(null);
        Delivery delivery = null;
        if (venta != null)
            delivery = venta.getDelivery();
        Double descuento = facturaLegal.getDescuento() != null ? facturaLegal.getDescuento() : 0.0;
        
        // Convertir todos los valores a moneda extranjera
        Double totalFinal = facturaLegal.getTotalFinal();
        Double totalIva10 = facturaLegal.getIvaParcial10();
        Double totalIva5 = facturaLegal.getIvaParcial5();
        Double totalIva = totalIva10 + totalIva5;
        
        // Convertir valores usando el tipo de cambio
        // Total parcial = total final + descuento (en guaraníes), luego convertir
        Double totalParcialGs = totalFinal + descuento;
        Double totalParcialExtranjera = totalParcialGs / tipoCambio;
        Double totalFinalExtranjera = totalFinal / tipoCambio;
        Double descuentoExtranjera = descuento / tipoCambio;
        Double totalIva10Extranjera = totalIva10 / tipoCambio;
        Double totalIva5Extranjera = totalIva5 / tipoCambio;
        Double totalIvaExtranjera = totalIva / tipoCambio;
        Double totalParcial0Extranjera = (facturaLegal.getTotalParcial0() != null ? facturaLegal.getTotalParcial0() : 0.0) / tipoCambio;

        if (printService != null) {
            printerOutputStream = this.printerOutputStream != null ? this.printerOutputStream
                    : new PrinterOutputStream(printService);
            // Styles
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style factura = new Style().setJustification(EscPosConst.Justification.Center)
                    .setFontSize(Style.FontSize._1, Style.FontSize._1);

            EscPos escpos = new EscPos(printerOutputStream);
            BitImageWrapper imageWrapper = new BitImageWrapper();
            Bitonal algorithm = new BitonalThreshold();
            
            escpos.writeLF("--------------------------------");
            escpos.writeLF(factura, facturaLegal.getTimbradoDetalle().getTimbrado().getRazonSocial().toUpperCase());
            escpos.writeLF(factura, "RUC: " + facturaLegal.getTimbradoDetalle().getTimbrado().getRuc());
            escpos.writeLF(factura, "Timbrado: " + facturaLegal.getTimbradoDetalle().getTimbrado().getNumero());

            // Si el timbrado es electronico, no se imprime la fecha de inicio y fin
            if (facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico() != true) {
                escpos.writeLF(factura, "De "
                        + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaInicio()
                                .format(impresionService.shortDate)
                        + " a "
                        + facturaLegal.getTimbradoDetalle().getTimbrado().getFechaFin()
                                .format(impresionService.shortDate));
            }

            Long numeroFacturaAux = Long.valueOf(facturaLegal.getNumeroFactura());
            StringBuilder numeroFacturaString = new StringBuilder();
            for (int i = 7; i > numeroFacturaAux.toString().length(); i--) {
                numeroFacturaString.append("0");
            }
            if (facturaLegal.getNumeroFactura() != null) {
                numeroFacturaString.append(facturaLegal.getNumeroFactura());
            } else {
                numeroFacturaString.append(numeroFacturaAux.toString());
            }
            escpos.writeLF(factura, "Nro: " + sucursal.getCodigoEstablecimientoFactura() + "-"
                    + facturaLegal.getTimbradoDetalle().getPuntoExpedicion() + "-" + numeroFacturaString.toString());
            escpos.writeLF(center, "Condicion: " + (facturaLegal.getCredito() == false ? "Contado" : "Crédito"));
            
            // Mostrar cambio utilizado
            escpos.writeLF(center.setBold(true), "Cambio: " + 
                String.format(Locale.GERMAN, "%.2f", tipoCambio) + " Gs/" + monedaExtranjera.toUpperCase());

            if (sucursal != null) {
                escpos.writeLF(center, "Suc: " + sucursal.getNombre());
                if (sucursal.getCiudad() != null) {
                    escpos.writeLF(center, sucursal.getCiudad().getDescripcion());
                    if (sucursal.getDireccion() != null) {
                        escpos.writeLF(center, sucursal.getNombre() + " - " + sucursal.getDireccion());
                    }
                }
            }
            if (venta != null)
                escpos.writeLF(center.setBold(true), "Venta: " + venta.getId());
            if (delivery != null) {
                escpos.writeLF(center, "Modo: Delivery");
            }
            if (venta != null && venta.getUsuario() != null) {
                escpos.writeLF("Cajero: " + venta.getUsuario().getPersona().getNombre());
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

            if (facturaLegal.getRuc() != null) {
                if (!facturaLegal.getRuc().contains("-")) {
                    facturaLegal.setRuc(facturaLegal.getRuc() + getDigitoVerificadorString(facturaLegal.getRuc()));
                }
            }

            escpos.writeLF("CI/RUC: " + facturaLegal.getRuc());
            if (facturaLegal.getDireccion() != null)
                escpos.writeLF("Dir: " + facturaLegal.getDireccion());

            escpos.writeLF("--------------------------------");

            escpos.writeLF("Producto");
            escpos.writeLF("Cant  IVA   P.U              P.T");
            escpos.writeLF("--------------------------------");
            for (FacturaLegalItem vi : facturaLegalItemList) {
                // Prioridad 1: IVA del item directamente
                Integer iva = vi.getIva();
                
                // Prioridad 2: IVA del producto vinculado directamente
                if (iva == null && vi.getProducto() != null) {
                    iva = vi.getProducto().getIva();
                }
                // Prioridad 3: IVA del producto a través de la presentación
                else if (iva == null && vi.getPresentacion() != null) {
                    iva = vi.getPresentacion().getProducto().getIva();
                }
                
                // Default 10% si no se puede determinar el IVA
                if (iva == null) {
                    iva = 10;
                }
                
                // Construir string de cantidad con unidad de medida si está disponible
                String cantidadStr;
                if (vi.getUnidadMedida() != null && !vi.getUnidadMedida().trim().isEmpty()) {
                    cantidadStr = vi.getCantidad().intValue() + " " + vi.getUnidadMedida() + " (" + vi.getCantidad() + ") " + iva + "%";
                } else {
                    cantidadStr = vi.getCantidad().intValue() + " (" + vi.getCantidad() + ") " + iva + "%";
                }
                
                escpos.writeLF(vi.getDescripcion());
                escpos.write(new Style().setBold(true), cantidadStr);
                
                // Convertir precios a moneda extranjera
                Double precioUnitarioExtranjera = vi.getPrecioUnitario() / tipoCambio;
                Double totalItemExtranjera = vi.getTotal() / tipoCambio;
                
                // Formatear con 2-3 decimales según necesidad
                String valorUnitario = formatearMonedaExtranjera(precioUnitarioExtranjera);
                String valorTotal = formatearMonedaExtranjera(totalItemExtranjera);
                
                for (int i = 14; i > cantidadStr.length(); i--) {
                    escpos.write(" ");
                }
                escpos.write(valorUnitario);
                for (int i = 16 - valorUnitario.length(); i > valorTotal.length(); i--) {
                    escpos.write(" ");
                }
                escpos.writeLF(valorTotal);
            }

            // Sección de totales en moneda extranjera
            escpos.writeLF("------------Totales-------------");
            escpos.write("   "); // 4 espacios para moneda
            escpos.write("   Parcial"); // 7 chars
            escpos.write("    "); // 2 espacios = 9 total
            escpos.write("Desc."); // 5 chars
            escpos.write("     "); // 4 espacios = 9 total
            escpos.writeLF("Final"); // 5 chars
            
            // Línea de moneda extranjera
            escpos.write(monedaExtranjera.toUpperCase() + ". ");
            String parcialExtStr = formatearMonedaExtranjera(totalParcialExtranjera);
            int espaciosParcialExt = 9 - parcialExtStr.length();
            for (int i = 0; i < espaciosParcialExt; i++) {
                escpos.write(" ");
            }
            escpos.write(parcialExtStr);
            
            String descExtStr = formatearMonedaExtranjera(descuentoExtranjera);
            int espaciosDescExt = 9 - descExtStr.length();
            for (int i = 0; i < espaciosDescExt; i++) {
                escpos.write(" ");
            }
            escpos.write(descExtStr);
            
            String finalExtStr = formatearMonedaExtranjera(totalFinalExtranjera);
            int espaciosFinalExt = 10 - finalExtStr.length();
            for (int i = 0; i < espaciosFinalExt; i++) {
                escpos.write(" ");
            }
            escpos.writeLF(finalExtStr);

            // Sección de liquidación IVA en moneda extranjera
            escpos.writeLF("--------Liquidacion IVA---------");
            escpos.write("Gravadas 10%:");
            String totalIva10ExtS = formatearMonedaExtranjera(totalIva10Extranjera);
            for (int i = 19; i > totalIva10ExtS.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva10ExtS);
            escpos.write("Gravadas 5%: ");
            String totalIva5ExtS = formatearMonedaExtranjera(totalIva5Extranjera);
            for (int i = 19; i > totalIva5ExtS.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva5ExtS);
            escpos.write("Exentas:     ");
            String totalIva0ExtS = formatearMonedaExtranjera(totalParcial0Extranjera);
            for (int i = 19; i > totalIva0ExtS.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalIva0ExtS);
            String totalFinalIvaExtS = formatearMonedaExtranjera(totalIvaExtranjera);
            escpos.write("Total IVA:   ");
            for (int i = 19; i > totalFinalIvaExtS.length(); i--) {
                escpos.write(" ");
            }
            escpos.writeLF(totalFinalIvaExtS);

            escpos.writeLF("--------------------------------");
            
            // Generar CDC si es documento electrónico
            if (facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico() != null
                    && facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico()) {

                DocumentoElectronico documentoElectronico = documentoElectronicoService
                        .findByFacturaLegalIdAndSucursalId(facturaLegal.getId(), facturaLegal.getSucursalId());

                String cdc = documentoElectronico.getCdc();
                String urlQr = documentoElectronico.getUrlQr();

                // Imprimir QR como imagen generada por ZXing
                if (urlQr != null) {
                    try {
                        log.info("Generando imagen de QR para URL de {} caracteres.", urlQr.length());
                        BufferedImage qrImage = QRCodeImageGenerator.generateQRCodeImage(urlQr, 250, 250);

                        imageWrapper.setJustification(EscPosConst.Justification.Center);
                        EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(qrImage), algorithm);

                        escpos.write(imageWrapper, escposImage);
                        escpos.feed(1);
                        log.info("Imagen de QR enviada a la impresora exitosamente.");

                    } catch (Exception e) {
                        log.error("No se pudo generar o imprimir la imagen del código QR. URL: {}", urlQr, e);
                        escpos.writeLF(center, "ERROR: No se pudo generar el código QR.");
                        escpos.writeLF(center, "URL de consulta:");
                        escpos.writeLF(center, urlQr);
                        escpos.feed(1);
                    }
                }

                escpos.writeLF(center,
                        "Consulte la validez de esta Factura Electronica con el numero de CDC impreso abajo en:");
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
            }
            escpos.feed(1);
            escpos.writeLF(center.setBold(true), "GRACIAS POR LA PREFERENCIA");
            escpos.feed(5);

            try {
                if (true) {
                    escpos.close();
                    printerOutputStream.close();
                    this.printerOutputStream = null;
                } else {
                    this.printerOutputStream = printerOutputStream;
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    /**
     * Formatea un valor en moneda extranjera con 2 decimales si no tiene más,
     * o 3 decimales redondeando hacia arriba si tiene más.
     * 
     * @param valor El valor a formatear
     * @return String formateado con 2 o 3 decimales según necesidad
     */
    private String formatearMonedaExtranjera(Double valor) {
        if (valor == null || valor.isNaN() || valor.isInfinite()) {
            return "0.00";
        }
        
        // Usar BigDecimal para precisión
        BigDecimal valorBD = BigDecimal.valueOf(valor);
        
        // Redondear a 2 decimales
        BigDecimal valor2Dec = valorBD.setScale(2, RoundingMode.HALF_UP);
        
        // Verificar si el valor tiene más de 2 decimales significativos
        // Si el valor original es diferente al redondeado a 2 decimales por más de 0.005,
        // significa que tiene decimales significativos más allá de 2
        BigDecimal diferencia = valorBD.subtract(valor2Dec).abs();
        BigDecimal umbral = new BigDecimal("0.005"); // Mitad del último decimal de 2 cifras
        
        // Si la diferencia es mayor al umbral, usar 3 decimales redondeando hacia arriba
        if (diferencia.compareTo(umbral) > 0) {
            // Tiene más decimales significativos, usar 3 decimales redondeando hacia arriba
            BigDecimal valor3Dec = valorBD.setScale(3, RoundingMode.UP);
            return String.format(Locale.GERMAN, "%.3f", valor3Dec.doubleValue());
        } else {
            // No tiene más decimales significativos, usar 2 decimales
            return String.format(Locale.GERMAN, "%.2f", valor2Dec.doubleValue());
        }
    }

    // Métodos CDC movidos a SifenService

    /**
     * El código de seguridad de los documentos electrónicos (campo dCodSeg) tiene
     * como objetivo asegurar la privacidad de los documentos emitidos, debe ser
     * generado por el contribuyente emisor, conforme a las siguientes condiciones:
     * • Debe ser un número positivo de 9 dígitos.
     * • Aleatorio.
     * • Debe ser distinto para cada DE y generado por un algoritmo de complejidad
     * suficiente para evitar la reproducción del valor.
     * • Rango NO SECUENCIAL entre 000000001 y 999999999.
     * • No tener relación con ninguna información específica o directa del DE o del
     * emisor de manera a garantizar su seguridad.
     * • No debe ser igual al número de documento campo dNumDoc.
     * • En caso de ser un número de menos de 9 dígitos completar con 0 a la
     * izquierda.
     */
    Integer getTipoContribuyente(Cliente cliente) {
        return cliente.getTipoContribuyente();
    }
    public DocumentoElectronico documentoElectronico(FacturaLegal facturaLegal) {
        if (facturaLegal != null && facturaLegal.getId() != null && facturaLegal.getSucursalId() != null) {
            return documentoElectronicoService.findByFacturaLegalIdAndSucursalId(facturaLegal.getId(),
                    facturaLegal.getSucursalId());
        }
        return null;
    }

    public Boolean tieneDocumentoElectronico(FacturaLegal facturaLegal) {
        return facturaLegal.tieneDocumentoElectronico();
    }

    /**
     * Genera un documento electrónico completo para una factura legal.
     * Este método utiliza la librería SIFEN para generar el CDC, URL QR y XML
     * firmado.
     *
     * @param facturaLegalId El ID de la factura legal
     * @param sucId          El ID de la sucursal
     * @return La factura legal actualizada con la información del documento
     *         electrónico
     */
    public FacturaLegal generarDocumentoElectronico(Long facturaLegalId, Long sucId) {
        try {
            FacturaLegal facturaLegal = service.findById(facturaLegalId)
                    .orElseThrow(() -> new RuntimeException("Factura legal no encontrada con ID: " + facturaLegalId));

            if (!facturaLegal.getSucursalId().equals(sucId)) {
                throw new RuntimeException("La factura legal no pertenece a la sucursal especificada");
            }
            FacturaLegal facturaActualizada = null;
            return facturaActualizada;

        } catch (Exception e) {
            log.error("Error al generar documento electrónico para factura legal ID: {}", facturaLegalId, e);
            throw new RuntimeException("Error al generar documento electrónico: " + e.getMessage(), e);
        }
    }

    private void enviarNotificacionFacturaAltoValor(FacturaLegal factura) {
        if (factura != null && factura.getId() != null && factura.getSucursalId() != null) {
            new Thread(() -> {
                try {
                    String clienteNombre = "N/A";
                    if (factura.getCliente() != null && factura.getCliente().getPersona() != null) {
                        clienteNombre = factura.getCliente().getPersona().getNombre();
                    } else if (factura.getNombre() != null && !factura.getNombre().trim().isEmpty()) {
                        clienteNombre = factura.getNombre();
                    }
                    
                    String clienteNombreEncoded = java.net.URLEncoder.encode(clienteNombre, "UTF-8");
                    
                    Double valorTotal = factura.getTotalFinal() != null ? factura.getTotalFinal() : 0.0;
                    
                    String servidorUrl = env.getProperty("servidor.url");
                    if (servidorUrl == null || servidorUrl.isEmpty()) {
                        System.err.println("La propiedad 'servidor.url' no está configurada en application.properties");
                        return;
                    }
                    String url = servidorUrl + "/notification/factura-alto-valor/" 
                        + factura.getId() + "/" 
                        + factura.getSucursalId() + "/"
                        + valorTotal + "/"
                        + clienteNombreEncoded;
                    
                    restTemplate.postForEntity(url, null, String.class);
                } catch (Exception e) {
                }
            }).start();
        }
    }

}
