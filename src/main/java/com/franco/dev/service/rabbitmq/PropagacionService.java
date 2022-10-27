package com.franco.dev.service.rabbitmq;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.graphql.configuraciones.publisher.SincronizacionStatusPublisher;
import com.franco.dev.graphql.financiero.ConteoGraphQL;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.rabbit.RabbitMQConection;
import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.rabbit.dto.SaveConteoDto;
import com.franco.dev.rabbit.dto.SaveFacturaDto;
import com.franco.dev.rabbit.enums.TipoAccion;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.rabbit.sender.Sender;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuracion.LocalService;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.empresarial.SectorService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.empresarial.ZonaService;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.general.BarrioService;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.*;
import com.franco.dev.service.productos.*;
import com.franco.dev.service.productos.pdv.PdvCategoriaService;
import com.franco.dev.service.productos.pdv.PdvGrupoService;
import com.franco.dev.service.productos.pdv.PdvGruposProductosService;
import com.franco.dev.service.utils.ImageService;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Service
public class PropagacionService {

    @Autowired
    public MovimientoStockService movimientoStockService;
    @Autowired
    public SectorService sectorService;
    @Autowired
    public ZonaService zonaService;
    Long sucursalVerificar = null;
    private Logger log = LoggerFactory.getLogger(PropagacionService.class);
    @Autowired
    private Environment env;
    @Autowired
    private Sender sender;
    @Autowired
    private SucursalService sucursalService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private PersonaService personaService;
    @Autowired
    private InitDbService initDbService;
    @Autowired
    private PaisService paisService;
    @Autowired
    private CiudadService ciudadService;
    @Autowired
    private FamiliaService familiaService;
    @Autowired
    private SubFamiliaService subFamiliaService;
    @Autowired
    private ProductoService productoService;
    @Autowired
    private TipoPresentacionService tipoPresentacionService;
    @Autowired
    private PresentacionService presentacionService;
    @Autowired
    private PdvCategoriaService pdvCategoriaService;
    @Autowired
    private PdvGrupoService pdvGrupoService;
    @Autowired
    private PdvGruposProductosService pdvGruposProductosService;
    @Autowired
    private TipoPrecioService tipoPrecioService;
    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;
    @Autowired
    private BancoService bancoService;
    @Autowired
    private CuentaBancariaService cuentaBancariaService;
    @Autowired
    private MonedaService monedaService;
    @Autowired
    private MonedaBilleteService monedaBilleteService;
    @Autowired
    private FormaPagoService formaPagoService;
    @Autowired
    private DocumentoService documentoService;
    @Autowired
    private MaletinService maletinService;
    @Autowired
    private CargoService cargoService;
    @Autowired
    private TipoGastoService tipoGastoService;
    @Autowired
    private CodigoService codigoService;
    @Autowired
    private CambioService cambioService;
    @Autowired
    private BarrioService barrioService;
    @Autowired
    private ContactoService contactoService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private FuncionarioService funcionarioService;
    @Autowired
    private ProveedorService proveedorService;
    @Autowired
    private VendedorService vendedorService;
    @Autowired
    private VendedorProveedorService vendedorProveedorService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private UsuarioRoleService usuarioRoleService;
    @Autowired
    private SincronizacionStatusPublisher sincronizacionStatusPublisher;
    @Autowired
    private LocalService localService;
    @Autowired
    private TransferenciaService transferenciaService;
    @Autowired
    private TransferenciaItemService transferenciaItemService;
    @Autowired
    private InventarioService inventarioService;
    @Autowired
    private InventarioProductoService inventarioProductoService;
    @Autowired
    private InventarioProductoItemService inventarioProductoItemService;
    @Autowired
    private ImageService imageService;
    @Autowired
    private PdvCajaService pdvCajaService;
    @Autowired
    private ConteoService conteoService;
    @Autowired
    private ConteoMonedaService conteoMonedaService;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ConteoGraphQL conteoGraphQL;
    @Autowired
    private FacturaLegalService facturaLegalService;
    @Autowired
    private FacturaLegalItemService facturaLegalItemService;
    @Autowired
    private FacturaLegalGraphQL facturaLegalGraphQL;
    @Autowired
    private VentaService ventaService;
    @Autowired
    private VentaItemService ventaItemService;
    @Autowired
    private MovimientoCajaService movimientoCajaService;

    //    public Boolean verficarConexion(Long sucId) {
//        sucursalVerificar = sucId;
//        RabbitDto dato = new RabbitDto(TipoAccion.VERIFICAR, TipoEntidad.LONG, sucId, null);
//        sender.enviar(RabbitMQConection., dato);
//    }

    public Boolean initDb() {
        return initDbService.initDb();
    }

    public void solicitarDB() {
        RabbitDto dto = new RabbitDto();
        dto.setIdSucursalOrigen(Long.valueOf(env.getProperty("sucursalId")));
        dto.setTipoAccion(TipoAccion.SOLICITAR_DB);
        dto.setTipoEntidad(TipoEntidad.USUARIO);
        sender.enviar(RabbitMQConection.SERVIDOR_KEY, dto);
    }

    public List<Sucursal> solicitarSucursales() {
        RabbitDto<List<Sucursal>> dto = new RabbitDto();
        dto.setIdSucursalOrigen(Long.valueOf(env.getProperty("sucursalId")));
        dto.setTipoAccion(TipoAccion.SOLICITAR_SUCURSALES);
        return (List<Sucursal>) sender.enviarAndRecibir(RabbitMQConection.SERVIDOR_KEY, dto);
    }

    public void guardarEntidades(RabbitDto dto) {
        log.info("recibiendo entidad para guardar");
        switch (dto.getTipoEntidad()) {
            case USUARIO:
                log.info("cargando usuarios: ");
                guardar(usuarioService, dto, TipoEntidad.PAIS);
                break;
            case PAIS:
                log.info("cargando Pais: ");
                guardar(paisService, dto, TipoEntidad.CIUDAD);
                break;
            case CIUDAD:
                log.info("cargando Ciudad: ");
                guardar(ciudadService, dto, TipoEntidad.PERSONA);
                break;
            case PERSONA:
                log.info("cargando Persona: ");
                guardar(personaService, dto, TipoEntidad.USUARIO_UPDATE);
                break;
            case USUARIO_UPDATE:
                log.info("cargando Persona: ");
                guardar(personaService, dto, TipoEntidad.FAMILIA);
                break;
            case FAMILIA:
                log.info("cargando Familia: ");
                guardar(familiaService, dto, TipoEntidad.SUBFAMILIA);
                break;
            case SUBFAMILIA:
                log.info("cargando subfamilia: ");
                guardar(subFamiliaService, dto, TipoEntidad.PRODUCTO);
                break;
            case PRODUCTO:
                log.info("cargando productos: ");
                guardar(productoService, dto, TipoEntidad.TIPO_PRESENTACION);
                break;
            case TIPO_PRESENTACION:
                log.info("cargando tipo presentacion: ");
                guardar(tipoPresentacionService, dto, TipoEntidad.PRESENTACION);
                break;
            case PRESENTACION:
                log.info("cargando presentacion: ");
                guardar(presentacionService, dto, TipoEntidad.PDV_CATEGORIA);
                break;
            case PDV_CATEGORIA:
                log.info("cargando pdv categoria: ");
                guardar(pdvCategoriaService, dto, TipoEntidad.PDV_GRUPO);
                break;
            case PDV_GRUPO:
                log.info("cargando pdv grupo: ");
                guardar(pdvGrupoService, dto, TipoEntidad.PDV_GRUPO_PRODUCTO);
                break;
            case PDV_GRUPO_PRODUCTO:
                log.info("cargando pdv grupo producto: ");
                guardar(pdvGruposProductosService, dto, TipoEntidad.SUCURSAL);
                break;
            case SUCURSAL:
                log.info("cargando sucursal: ");
                guardar(sucursalService, dto, TipoEntidad.TIPO_PRECIO);
                break;
            case TIPO_PRECIO:
                log.info("cargando tipo precio: ");
                guardar(tipoPrecioService, dto, TipoEntidad.PRECIO_POR_SUCURSAL);
                break;
            case PRECIO_POR_SUCURSAL:
                log.info("cargando precio por sucursal: ");
                guardar(precioPorSucursalService, dto, TipoEntidad.BANCO);
                break;
            case BANCO:
                log.info("cargando banco: ");
                guardar(bancoService, dto, TipoEntidad.MONEDA);
                break;
            case MONEDA:
                log.info("cargando moneda: ");
                guardar(monedaService, dto, TipoEntidad.MONEDAS_BILLETES);
                break;
            case MONEDAS_BILLETES:
                log.info("cargando moneda billetes: ");
                guardar(monedaBilleteService, dto, TipoEntidad.CUENTA_BANCARIA);
                break;
            case CUENTA_BANCARIA:
                log.info("cargando cuenta bancaria: ");
                guardar(cuentaBancariaService, dto, TipoEntidad.FORMA_DE_PAGO);
                break;
            case FORMA_DE_PAGO:
                log.info("cargando forma pago: ");
                guardar(formaPagoService, dto, TipoEntidad.DOCUMENTO);
                break;
            case DOCUMENTO:
                log.info("cargando docuemento: ");
                guardar(documentoService, dto, TipoEntidad.MALETIN);
                break;
            case MALETIN:
                log.info("cargando maletin: ");
                guardar(maletinService, dto, TipoEntidad.CARGO);
                break;
            case CARGO:
                log.info("cargando cargo: ");
                guardar(cargoService, dto, TipoEntidad.TIPO_GASTO);
                break;
            case TIPO_GASTO:
                log.info("cargando tipo gasto: ");
                guardar(tipoGastoService, dto, TipoEntidad.TIPO_GASTO_UPDATE);
                break;
            case TIPO_GASTO_UPDATE:
                log.info("cargando tipo gasto: ");
                guardar(tipoGastoService, dto, TipoEntidad.CODIGO);
                break;
            case CODIGO:
                log.info("cargando codigo: ");
                guardar(codigoService, dto, TipoEntidad.CAMBIO);
                break;
            case CAMBIO:
                log.info("cargando cambio: ");
                guardar(cambioService, dto, TipoEntidad.BARRIO);
                break;
            case BARRIO:
                log.info("cargando barrio: ");
                guardar(barrioService, dto, TipoEntidad.CONTACTO);
                break;
            case CONTACTO:
                log.info("cargando contacto: ");
                guardar(contactoService, dto, TipoEntidad.CLIENTE);
                break;
            case CLIENTE:
                log.info("cargando cliente: ");
                guardar(clienteService, dto, TipoEntidad.FUNCIONARIO);
                break;
            case FUNCIONARIO:
                log.info("cargando funncioario: ");
                guardar(funcionarioService, dto, TipoEntidad.PROVEEDOR);
                break;
            case PROVEEDOR:
                log.info("cargando proveedor: ");
                guardar(proveedorService, dto, TipoEntidad.VENDEDOR);
                break;
            case VENDEDOR:
                log.info("cargando vendedor: ");
                guardar(vendedorService, dto, TipoEntidad.ROLE);
                break;
            case ROLE:
                log.info("cargando role: ");
                guardar(roleService, dto, TipoEntidad.USUARIO_ROLE);
                break;
            case USUARIO_ROLE:
                log.info("cargando usuario role: ");
                guardar(usuarioRoleService, dto, null);
                solicitarResources();
                break;
            default:
                break;
        }
    }

    public Object crudEntidad(RabbitDto dto) {
        log.info("recibiendo entidad para guardar");
        dto.setRecibidoEnFilial(true);
        switch (dto.getTipoEntidad()) {
            case USUARIO:
                log.info("cargando usuario: ");
                return guardar(usuarioService, dto);
            case PAIS:
                log.info("cargando Pais: ");
                return guardar(paisService, dto);

            case CIUDAD:
                log.info("cargando Ciudad: ");
                return guardar(ciudadService, dto);

            case PERSONA:
                log.info("cargando Persona: ");
                return guardar(personaService, dto);

            case FAMILIA:
                log.info("cargando Familia: ");
                return guardar(familiaService, dto);

            case SUBFAMILIA:
                log.info("cargando subfamilia: ");
                return guardar(subFamiliaService, dto);

            case PRODUCTO:
                log.info("cargando productos: ");
                return guardar(productoService, dto);

            case TIPO_PRESENTACION:
                log.info("cargando tipo presentacion: ");
                return guardar(tipoPresentacionService, dto);

            case PRESENTACION:
                log.info("cargando presentacion: ");
                return guardar(presentacionService, dto);

            case PDV_CATEGORIA:
                log.info("cargando pdv categoria: ");
                return guardar(pdvCategoriaService, dto);

            case PDV_GRUPO:
                log.info("cargando pdv grupo: ");
                return guardar(pdvGrupoService, dto);

            case PDV_GRUPO_PRODUCTO:
                log.info("cargando pdv grupo producto: ");
                return guardar(pdvGruposProductosService, dto);

            case SUCURSAL:
                log.info("cargando sucursal: ");
                return guardar(sucursalService, dto);

            case TIPO_PRECIO:
                log.info("cargando tipo precio: ");
                return guardar(tipoPrecioService, dto);

            case PRECIO_POR_SUCURSAL:
                log.info("cargando precio por sucursal: ");
                return guardar(precioPorSucursalService, dto);

            case BANCO:
                log.info("cargando banco: ");
                return guardar(bancoService, dto);

            case MONEDA:
                log.info("cargando moneda: ");
                return guardar(monedaService, dto);

            case MONEDAS_BILLETES:
                log.info("cargando moneda billetes: ");
                return guardar(monedaBilleteService, dto);

            case CUENTA_BANCARIA:
                log.info("cargando cuenta bancaria: ");
                return guardar(cuentaBancariaService, dto);

            case FORMA_DE_PAGO:
                log.info("cargando forma pago: ");
                return guardar(formaPagoService, dto);

            case DOCUMENTO:
                log.info("cargando docuemento: ");
                return guardar(documentoService, dto);

            case MALETIN:
                log.info("cargando maletin: ");
                return guardar(maletinService, dto);

            case CARGO:
                log.info("cargando cargo: ");
                return guardar(cargoService, dto);

            case TIPO_GASTO:
                log.info("cargando tipo gasto: ");
                return guardar(tipoGastoService, dto);

            case TIPO_GASTO_UPDATE:
                log.info("cargando tipo gasto: ");
                return guardar(tipoGastoService, dto);

            case CODIGO:
                log.info("cargando codigo: ");
                return guardar(codigoService, dto);

            case CAMBIO:
                log.info("cargando cambio: ");
                return guardar(codigoService, dto);

            case BARRIO:
                log.info("cargando barrio: ");
                return guardar(barrioService, dto);

            case CONTACTO:
                log.info("cargando contacto: ");
                return guardar(contactoService, dto);

            case CLIENTE:
                log.info("cargando cliente: ");
                return guardar(clienteService, dto);

            case FUNCIONARIO:
                log.info("cargando funncioario: ");
                return guardar(funcionarioService, dto);

            case PROVEEDOR:
                log.info("cargando proveedor: ");
                return guardar(proveedorService, dto);

            case VENDEDOR:
                log.info("cargando vendedor: ");
                return guardar(vendedorService, dto);

            case ROLE:
                log.info("cargando role: ");
                return guardar(roleService, dto);

            case USUARIO_ROLE:
                log.info("cargando usuario role: ");
                return guardar(usuarioRoleService, dto);

            case LOCAL:
                log.info("creando local: ");
                guardarLocal((Sucursal) dto.getEntidad());
                return null;

            case TRANSFERENCIA:
                log.info("creando transferencia: ");
                return guardar(transferenciaService, dto);

            case TRANSFERENCIA_ITEM:
                log.info("creando transferencia item: ");
                return guardar(transferenciaItemService, dto);

            case INVENTARIO:
                log.info("creando INVENTARIO item: ");
                return guardar(inventarioService, dto);

            case INVENTARIO_PRODUCTO:
                log.info("creando innventario producto item: ");
                return guardar(inventarioProductoService, dto);

            case INVENTARIO_PRODUCTO_ITEM:
                log.info("creando inventario producto item item: ");
                return guardar(inventarioProductoItemService, dto);

            case MOVIMIENTO_STOCK:
                log.info("creando movimiento stock: ");
                return guardar(movimientoStockService, dto);
            case SECTOR:
                log.info("creando sector: ");
                return guardar(sectorService, dto);
            case ZONA:
                log.info("creando zona: ");
                return guardar(zonaService, dto);
            case CONTEO:
                log.info("guardando conteo");
//                return guardar(conteoService, dto);
                SaveConteoDto conteoDto = (SaveConteoDto) dto.getEntidad();
                Conteo conteo = conteoGraphQL.saveConteo(conteoDto.getConteoInput(), conteoDto.getConteoMonedaInputList(), conteoDto.getCajaId(), conteoDto.getApertura());
                if (conteo != null) {
                    pdvCajaService.imprimirBalance(conteoDto.getCajaId(), null, null);
                    return conteo;
                }
            case CONTEO_ITEM:
                log.info("guardando conteo");
                return guardar(conteoMonedaService, dto);
            case PDV_CAJA:
                log.info("guardando caja: ");
                return guardar(pdvCajaService, dto);
            case FACTURA:
                log.info("guardando factura: ");
                return guardar(facturaLegalService, dto);
            case FACTURA_ITEM:
                log.info("guardando factura item: ");
                return guardar(facturaLegalItemService, dto);
            case VENTA:
                log.info("creando venta: ");
                return guardar(ventaService, dto);
            case VENTA_ITEM:
                log.info("creando venta item: ");
                return guardar(ventaItemService, dto);
            case MOVIMIENTO_CAJA:
                log.info("creando mov caja: ");
                return guardar(movimientoCajaService, dto);
            default:
                return null;
        }
    }

    public <T> void guardar(CrudService service, RabbitDto dto, TipoEntidad nextTipo) {
        List<T> list = service.saveAll((List<T>) dto.getEntidad());
        sender.enviar(RabbitMQConection.SERVIDOR_KEY, new RabbitDto(null, TipoAccion.SOLICITAR_DB, nextTipo, Long.valueOf(env.getProperty("sucursalId"))));
    }

    public void guardarImagen(RabbitDto dto, TipoEntidad tipoEntidad) {
        String filename = (String) dto.getEntidad();
        switch (tipoEntidad) {
            case PRESENTACION:
                try {
                    imageService.saveImageToPath((String) dto.getEntidad(), (String) dto.getData(), imageService.imagePresentaciones, imageService.imagePresentacionesThumbPath, true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    public void guardarArchivo(RabbitDto dto) {
        byte[] fileContent = (byte[]) dto.getData();
        try {
            FileUtils.writeByteArrayToFile(new File(imageService.storageDirectoryPath + dto.getEntidad() + ".zip"), fileContent);
            ZipUtil.explode(new File(imageService.storageDirectoryPath + dto.getEntidad() + ".zip", imageService.storageDirectoryPath + dto.getEntidad()));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public <T> Object guardar(CrudService service, RabbitDto dto) {
        switch (dto.getTipoAccion()) {
            case GUARDAR:
                T nuevaEntidad = dto.getRecibidoEnFilial()!=true ? (T) service.saveAndSend(dto.getEntidad(), false): (T) service.save(dto.getEntidad());
                if (nuevaEntidad != null) {
                    log.info("guardado con exito");
                }
                return nuevaEntidad;
            case DELETE:
                Boolean ok = false;
                if (dto.getEntidad() instanceof Long) {
                    ok = service.deleteById((Long) dto.getEntidad());
                } else {
                    ok = service.delete(dto.getEntidad());
                }
                if (ok) {
                    log.info("eliminado con exito");
                }
                return ok;
            default:
                return null;
        }
    }

    public <T> void propagarEntidad(T entity, TipoEntidad tipoEntidad) {
        log.info("Propagando entidad a servidor: " + tipoEntidad.name());
        sender.enviar(RabbitMQConection.SERVIDOR_KEY, new RabbitDto(entity, TipoAccion.GUARDAR, tipoEntidad, Long.valueOf(env.getProperty("sucursalId"))));
    }

    public <T> void propagarEntidad(T entity, TipoEntidad tipoEntidad, Boolean recibir) {
        log.info("Propagando entidad a servidor: " + tipoEntidad.name());
        sender.enviar(RabbitMQConection.SERVIDOR_KEY, new RabbitDto(entity, TipoAccion.GUARDAR, tipoEntidad, Long.valueOf(env.getProperty("sucursalId")), false, !recibir));
    }

    public <T> Object propagarEntidadAndRecibir(T entity, TipoEntidad tipoEntidad) {
        log.info("Propagando entidad a servidor y recibir: " + tipoEntidad.name());
        return sender.enviarAndRecibir(RabbitMQConection.SERVIDOR_KEY, new RabbitDto(entity, TipoAccion.GUARDAR, tipoEntidad, Long.valueOf(env.getProperty("sucursalId"))));
    }

    public <T> void deleteEntidad(T entity, TipoEntidad tipoEntidad) {
        log.info("Propagando entidad a servidor: " + tipoEntidad.name());
        sender.enviar(RabbitMQConection.SERVIDOR_KEY, new RabbitDto(entity, TipoAccion.DELETE, tipoEntidad, Long.valueOf(env.getProperty("sucursalId"))));
    }

    public void guardarLocal(Sucursal sucursal) {
        localService.save(new Local(null, null, null, sucursal, null));
    }

    public Inventario finalizarInventario(RabbitDto<Inventario> dto) {
        return inventarioService.finalizarInventario(dto.getEntidad().getId());
    }

    public void verificarResourcesExists() {
        Path path = Paths.get(imageService.getResourcesPath());
        if (!Files.exists(path)) {
            solicitarResources();
        }
    }

    public void solicitarResources() {
//        String url = "http://" + env.getProperty("ipServidorCentral") + "/config/resources";
//        log.info("solicitando recursos a " + url);
//        restTemplate.getMessageConverters().add(
//                new ByteArrayHttpMessageConverter());
//        HttpHeaders headers = new HttpHeaders();
//        headers.setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM));
//
//        HttpEntity<String> entity = new HttpEntity<String>(headers);
//
//        try {
//            ResponseEntity<byte[]> response = restTemplate.exchange(
//                    url,
//                    HttpMethod.GET, entity, byte[].class, "1");
//
//            if (response.getStatusCode() == HttpStatus.OK) {
//                try {
//                    Files.write(Paths.get(imageService.getResourcesPath() + ".zip"), response.getBody());
//                    ZipUtil.unpack(new File(imageService.getResourcesPath() + ".zip"), new File(imageService.getResourcesPath()));
//                    imageService.deleteFile(imageService.getResourcesPath() + ".zip");
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        } catch (RestClientException e) {
//            e.printStackTrace();
//        }

        sender.enviar(RabbitMQConection.SERVIDOR_KEY, new RabbitDto(null, TipoAccion.SOLICITAR_RESOURCES, null, Long.valueOf(env.getProperty("sucursalId"))));

    }

    public Object solicitarGuardar(RabbitDto dto) {
        return sender.enviarAndRecibir(RabbitMQConection.SERVIDOR_KEY, dto);
    }

    public Object cajaAbiertaPorUsuario(Long id) {
        log.info("Enviando caja a servidor del usuario: " + id);
        PdvCaja pdvCaja = pdvCajaService.findByUsuarioIdAndAbierto(id);
        if (pdvCaja != null) {
            if (pdvCaja.getConteoApertura() != null)
                pdvCaja.setConteoApertura(conteoService.findById(pdvCaja.getConteoApertura().getId()).orElse(null));
            if (pdvCaja.getUsuario() != null)
                pdvCaja.setUsuario(usuarioService.findById(pdvCaja.getUsuario().getId()).orElse(null));
            if (pdvCaja.getMaletin() != null)
                pdvCaja.setMaletin(maletinService.findById(pdvCaja.getMaletin().getId()).orElse(null));
        }
        return pdvCaja;
    }

    public Maletin maletinPorDescripcion(String texto) {
        return maletinService.findByDescripcion(texto);
    }

    public void propagarFactura(SaveFacturaDto saveFacturaDto) {
        log.info("Propagando facturas a servidor y recibir: ");
        sender.enviar(RabbitMQConection.SERVIDOR_KEY, new RabbitDto(saveFacturaDto, TipoAccion.GUARDAR, TipoEntidad.FACTURA, Long.valueOf(env.getProperty("sucursalId"))));
    }

    public Cliente solicitarCliente(Long id) {
        return (Cliente) sender.enviarAndRecibir(RabbitMQConection.SERVIDOR_KEY, new RabbitDto(id, TipoAccion.SOLICITAR_ENTIDAD, TipoEntidad.CLIENTE, Long.valueOf(env.getProperty("sucursalId"))));
    }
}
