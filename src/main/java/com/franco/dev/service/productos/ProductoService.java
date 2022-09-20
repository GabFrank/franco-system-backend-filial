package com.franco.dev.service.productos;

import com.franco.dev.domain.dto.ProductoReportDto;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.repository.productos.ProductoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.utils.ImageService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.*;

@Service
@AllArgsConstructor
public class ProductoService extends CrudService<Producto, ProductoRepository> {

    @Autowired
    private final ProductoRepository repository;

//    private Sender<ProductoInput> sender;
    @Autowired
    private final UsuarioService usuarioService;
    @Autowired
    private final SubFamiliaService subFamiliaService;
//    private final PersonaPublisher personaPublisher;
    @Autowired
    private final MovimientoStockService movimientoStockService;
    @Autowired
    private final PresentacionService presentacionService;
    @Autowired
    private final PrecioPorSucursalService precioPorSucursalService;
    @Autowired
    private final ImageService imageService;
    @Autowired
    private Environment env;
    private RabbitTemplate rabbitTemplate;

    @Override
    public ProductoRepository getRepository() {
        return repository;
    }


    public List<Producto> findByAll(String texto, Integer offset, Boolean isEnvase) {
        if (texto.length() > 0) {
            texto = texto.replace(' ', '%').toUpperCase();
            if (offset == null) {
                offset = 0;
            }
            if (isEnvase != null && isEnvase == true) {
                return repository.findEnvases(texto, offset, true);
            } else {
                return repository.findbyAll(texto, offset);
            }
        }
        return new ArrayList<>();
    }

    public Producto encontrarProductoCentralFilial(Producto pCentral) {
        Producto pFilial = findByIdCentral(pCentral.getId());
        log.info("recibiendo " + pCentral.getId());
        if (pFilial == null) {
            pFilial = findByDescripcionn(pCentral.getDescripcion());
            if (pFilial == null) {
                pFilial = pCentral;
                pFilial.setIdCentral(pFilial.getId());
                pFilial.setId(null);
                pFilial = repository.save(pFilial);
            } else {
                if (pFilial.getIdCentral() == null) {
                    pFilial.setIdCentral(pCentral.getId());
                    pFilial = repository.save(pFilial);
                } else {

                }
            }
        } else {
            pCentral.setIdCentral(pCentral.getId());
            pCentral.setId(pFilial.getId());
            pFilial = repository.save(pCentral);
        }
        return pFilial;
    }

    public Producto save(ProductoInput entity) throws GraphQLException {
        Producto p = null;
        ModelMapper m = new ModelMapper();
        m.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        Producto e = m.map(entity, Producto.class);
        if (entity.getUsuarioId() != null) e.setUsuario(usuarioService.findById(entity.getUsuarioId()).orElse(null));
        if (entity.getSubfamiliaId() != null)
            e.setSubfamilia(subFamiliaService.findById(entity.getSubfamiliaId()).orElse(null));
        if (e.getDescripcionFactura() != null) e.setDescripcionFactura(e.getDescripcion().toUpperCase());
        if (e.getImagenes() == null) e.setImagenes("/productos");
        if (entity.getEnvaseId() != null) e.setEnvase(findById(entity.getEnvaseId()).orElse(null));
        e.setDescripcion(e.getDescripcion().toUpperCase());

//        if(entity.getIdCentral()!=null){
//            p = repository.findByIdCentral(entity.getIdCentral());
//            log.info("id central no es null");
//            if(p!=null) log.info("Producto encontrado por id central");
//        }
//        if(p==null) {
//            p = repository.findByDescripcion(entity.getDescripcion());
//            if(p!=null) log.info("Producto encontrado por descripcion");
//        }
//        if(p!=null) {
//            e.setId(p.getId());
//        }
        p = repository.save(e);
//        if(!entity.getPropagado() && p!=null){
//            entity.setIdSucursalOrigen(Long.valueOf(env.getProperty("sucursalId")));
//            propagar(entity);
//        }
        return p;
    }

//    public Boolean deleteByInput(ProductoInput input, Long sucId) {
//        if(sucId == null || sucId != Long.valueOf(env.getProperty("sucursalId"))){
//            Long idProd = findByIdCentral(input.getIdCentral()).getId();
//            if(idProd!=null){
//                if(super.deleteById(idProd)){
//                    propagarDelete(input.getIdCentral());
//                    return true;
//                } else {
//                    throw new GraphQLException("No se pudo eliminar este producto");
//                }
//            }
//        }
//
//        return false;
//    }

    public List<Producto> findByProveedorId(Long id, String text) {
        return repository.findByProveedorId(id, text);
    }

    public List<Producto> findBySubFamiliaId(Long id) {
        return repository.findBySubfamiliaId(id);
    }

    public Producto findByCodigo(String texto) {
        return repository.findByCodigo(texto);
    }

    ;

//    public String propagar(ProductoInput input){
//        log.info("enviando producto a central");
//        log.info(input.toString());
//        RabbitDto<ProductoInput> dto = new RabbitDto();
//        dto.setAccion(GUARDAR);
//        dto.setTipo(Receiver.PRODUCTO);
//        dto.setEntidad(input);
//        sender.send(dto, "central");
//        return "Success";
//    }
//
//    public String propagarDelete(Long idCentral){
//        log.info("propagando delete a central");
//        RabbitDto<ProductoInput> dto = new RabbitDto();
//        dto.setAccion(ELIMINAR);
//        dto.setTipo(Receiver.PRODUCTO);
//        dto.setIdSucursalOrigen(Long.valueOf(env.getProperty("sucursalId")));
//        ProductoInput input = new ProductoInput();
//        input.setIdCentral(idCentral);
//        dto.setEntidad(input);
//        sender.send(dto, "central");
//        return "Success";
//    }

//    public void receive(RabbitDto dto) {
//        log.info("recibiendo producto");
//        log.info("accion: " + dto.getAccion());
//        ProductoInput input = new ProductoInput();
//        input = input.converHashMapToInput(dto.getEntidad());
//        if(dto.getAccion().equals(GUARDAR)) this.save(input);
//        else if(dto.getAccion().equals(ELIMINAR)) deleteByInput(input, dto.getIdSucursalOrigen());
//    }

    public Producto findByIdCentral(Long id) {
        return repository.findByIdCentral(id);
    }

    public List<Producto> findAllForPdv() {
        return repository.findAllForPdv();
    }

    public String exportarReporte(String texto) throws FileNotFoundException {
        List<Producto> productoList = findByAll(texto, 0, false);
        List<ProductoReportDto> productosDtoList = new ArrayList<>();
        PrecioPorSucursal precioVenta = null;
        PrecioPorSucursal precioCosto = null;
        for (Producto p : productoList) {
            Float stock = movimientoStockService.stockByProductoId(p.getId());
            Presentacion presentacion = presentacionService.findByPrincipalAndProductoId(true, p.getId());
            if (presentacion != null) {
                precioVenta = precioPorSucursalService.findPrincipalByPrecionacionId(presentacion.getId());
            }
            ProductoReportDto dto = new ProductoReportDto();
            dto.setId(p.getId());
            dto.setDescripcion(p.getDescripcion());
            dto.setPrecioCosto("0.0");
            dto.setPrecioVenta(NumberFormat.getNumberInstance(Locale.GERMAN).format(precioVenta.getPrecio().intValue()));
            productosDtoList.add(dto);

        }

        try {
            File file = ResourceUtils.getFile("classpath:productos.jrxml");
            try {
                JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
                JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(productosDtoList);
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("creadoPor", "Gabriel Franco");
                JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
                JasperExportManager.exportReportToPdfFile(jasperPrint, imageService.storageDirectoryPathReports + "productos.pdf");
                try {
                    File pdf = new File(imageService.storageDirectoryPathReports + "productos.pdf");
                    byte[] bytes = Files.readAllBytes(pdf.toPath());
                    String b64 = Base64.getEncoder().encodeToString(bytes);
                    return b64;
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } catch (JRException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "Reporte no generado";
    }

    public List<Producto> findByGrupoProductoId(Long id) {
        return repository.findByPdvGrupoProductoId(id);
    }

    public Producto findByDescripcionn(String texto) {
        return repository.findByDescripcion(texto);
    }

}
