package com.franco.dev.service.rabbitmq;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.graphql.configuraciones.publisher.SincronizacionStatusPublisher;
import com.franco.dev.rabbit.RabbitMQConection;
import com.franco.dev.rabbit.dto.RabbitDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class PropagacionService {

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
    public MovimientoStockService movimientoStockService;

    @Autowired
    public SectorService sectorService;

    @Autowired
    public ZonaService zonaService;

    @Autowired
    private ImageService imageService;

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
                break;
            default:
                break;
        }
    }

    public Object crudEntidad(RabbitDto dto) {
        log.info("recibiendo entidad para guardar");
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
        switch (tipoEntidad){
            case PRESENTACION:
                try {
                    imageService.saveImageToPath((String)dto.getEntidad(), (String)dto.getData(), true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    public <T> Object guardar(CrudService service, RabbitDto dto) {
        switch (dto.getTipoAccion()) {
            case GUARDAR:
                T nuevaEntidad = (T) service.save(dto.getEntidad());
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

    public <T> void deleteEntidad(T entity, TipoEntidad tipoEntidad) {
        log.info("Propagando entidad a servidor: " + tipoEntidad.name());
        sender.enviar(RabbitMQConection.SERVIDOR_KEY, new RabbitDto(entity, TipoAccion.DELETE, tipoEntidad, Long.valueOf(env.getProperty("sucursalId"))));
    }

    public void guardarLocal(Sucursal sucursal) {
        localService.save(new Local(null, null, null, sucursal, null));
    }

    public Inventario finalizarInventario(RabbitDto<Inventario> dto){
        return inventarioService.finalizarInventario(dto.getEntidad().getId());
    }
}
