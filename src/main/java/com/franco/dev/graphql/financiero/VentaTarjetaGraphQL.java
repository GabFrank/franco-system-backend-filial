package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.graphql.financiero.input.CompletarVentaTarjetaInput;
import com.franco.dev.graphql.financiero.input.SenaCuponInput;
import com.franco.dev.graphql.financiero.input.VentaTarjetaInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.TerminalPosService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.VentaTarjetaService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.impresion.dto.SenaCuponDto;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.util.List;

/**
 * VentaTarjeta en filial: el POS crea aca el registro PENDIENTE (funciona sin
 * internet) y la replicacion BRANCH_TO_MAIN lo sube al central, donde la app
 * movil lo completa. A diferencia del resolver del central, save no espera
 * sincronizacion de la venta: la venta vive en esta misma base.
 */
@Component
public class VentaTarjetaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VentaTarjetaGraphQL.class);

    @Autowired
    private VentaTarjetaService service;

    /**
     * Para no confiar en el {@code sucId} que manda el cliente.
     * <p>
     * Un filial atiende una sola sucursal y lo sabe sin preguntarle a nadie. Aceptar el valor del
     * cliente tal cual es lo que hacia este resolver, al reves de lo que hacen
     * {@code VentaGraphQL} y {@code GastoGraphQL} en este mismo repo. Importa porque
     * {@code venta_tarjeta} es BRANCH_TO_MAIN con PK compuesta {@code (id, sucursal_id)}: una
     * fila escrita con la sucursal de otro filial sube a central con atribucion falsa.
     */
    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private TerminalPosService terminalPosService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ImpresionService impresionService;

    public VentaTarjeta ventaTarjetaPorId(Long id, Long sucId) {
        return service.findByIdAndSucursalId(id, sucId);
    }

    public VentaTarjeta ventaTarjetaPorVentaId(Long ventaId, Long sucId) {
        return service.findByVentaIdAndSucursalId(ventaId, sucId);
    }

    public List<VentaTarjeta> ventasTarjetaPorCaja(Long cajaId, Long sucId) {
        return service.findByCajaIdAndSucursalId(cajaId, sucId);
    }

    /**
     * Pre-chequeo del cupon, para que el PDV pueda avisar ANTES de dar el escaneo por bueno.
     * Devuelve el motivo, o null si el cupon esta libre.
     * <p>
     * NO reemplaza la validacion del guardado: esa sigue corriendo igual y es la que manda. Esto
     * solo adelanta el aviso — entre el escaneo y el saveVenta puede pasar cualquier cosa, y
     * confiar en un chequeo del cliente seria un agujero.
     */
    public Page<VentaTarjeta> filtrarVentasTarjetaPorCaja(Long cajaId, Long sucId, String estado,
                                                         Long terminalPosId, Long monedaId,
                                                         Double montoDesde, Double montoHasta,
                                                         Long usuarioId,
                                                         Integer page, Integer size) {
        return service.filtrarPorCaja(
                cajaId, sucId, estado, terminalPosId, monedaId,
                montoDesde != null ? BigDecimal.valueOf(montoDesde) : null,
                montoHasta != null ? BigDecimal.valueOf(montoHasta) : null,
                usuarioId,
                page != null ? page : 0,
                size != null ? size : 15);
    }

    /**
     * El pre-chequeo del cupon, antes de que el cajero de el dato por bueno.
     * <p>
     * `montoEscaneado` no se pide aca a proposito: en el pre-chequeo el cajero muchas veces
     * todavia no lo tiene --acaba de escanear o de tipear el codigo-- y sin monto el chequeo por
     * codigo de autorizacion avisa de mas, que es el lado correcto para equivocarse en una
     * advertencia. Al guardar, `completar` lo pasa y el filtro se afina.
     */
    public String motivoCuponNoUsable(String qrCrudo, String identificadorTransaccion,
                                      String codigoAutorizacion, Long terminalPosId, Long sucId) {
        return service.motivoCuponNoUsable(null, null, sucursalService.exigirSucursalPropia(sucId),
                        identificadorTransaccion, qrCrudo, codigoAutorizacion, null, terminalPosId)
                .orElse(null);
    }

    public Long countVentasTarjetaSinRegistrar(Long cajaId, Long sucId) {
        Long count = service.countVentasTarjetaSinRegistrar(cajaId, sucId);
        return count != null ? count : 0L;
    }

    public VentaTarjeta saveVentaTarjeta(VentaTarjetaInput input) {
        VentaTarjeta entity = new VentaTarjeta();
        entity.setId(input.getId());
        entity.setSucursalId(input.getSucursalId());
        entity.setVentaId(input.getVentaId());
        entity.setCajaId(input.getCajaId());
        entity.setCodigoAutorizacion(input.getCodigoAutorizacion());
        entity.setNumeroBoleta(input.getNumeroBoleta());
        entity.setMonto(input.getMonto());
        entity.setMontoEscaneado(input.getMontoEscaneado());
        entity.setImagenUrl(input.getImagenUrl());
        entity.setEstado(input.getEstado() != null ? input.getEstado() : "PENDIENTE");
        if (input.getTerminalPosId() != null) {
            entity.setTerminalPos(terminalPosService.findById(input.getTerminalPosId()).orElse(null));
        }
        // La moneda del COBRO, no la de la terminal: es el cobro el que se esta pagando, y sin
        // ella monto/monto_escaneado quedan sin unidad.
        if (input.getMonedaId() != null) {
            entity.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(entity);
    }

    /**
     * Completa un PENDIENTE con los datos que vienen del QR impreso por el POS, leidos con el
     * lector del PDV. Es el reemplazo del camino foto+OCR desde el celular.
     * <p>
     * Deliberadamente NO reusa saveVentaTarjeta: ese arma la entidad de cero y dejaria en null
     * ventaId, cajaId, monto, terminalPos y usuario. Ver VentaTarjetaService#completar, que
     * ademas valida que el estado sea PENDIENTE (el updateVentaTarjeta del central no valida).
     */
    public VentaTarjeta completarVentaTarjeta(CompletarVentaTarjetaInput input) {
        return service.completar(
                input.getId(),
                sucursalService.exigirSucursalPropia(input.getSucursalId()),
                input.getCodigoAutorizacion(),
                input.getNumeroBoleta(),
                input.getMontoEscaneado(),
                input.getIdentificadorTransaccion(),
                input.getQrCrudo(),
                input.getCobroDetalleId(),
                input.getMonedaId(),
                input.getOrigen(),
                input.getCapturaToken(),
                input.getDatosExtra());
    }

    public Boolean cancelarVentaTarjetaPorVentaId(Long ventaId, Long sucId) {
        List<VentaTarjeta> registros = service.findAllByVentaIdAndSucursalId(ventaId, sucId);
        if (registros == null || registros.isEmpty()) return false;
        registros.forEach(vt -> {
            vt.setEstado("CANCELADO");
            service.save(vt);
        });
        return true;
    }

    /**
     * Deja sin conciliar TODOS los pendientes de una caja, con un motivo.
     *
     * <p>`motivo` y `usuarioId` son opcionales en el schema y obligatorios en la practica: el
     * mobile es un cliente mas viejo que no los manda, y un input nuevo obligatorio lo dejaria sin
     * poder cerrar caja. Los manda el desktop, que es de donde sale el cierre.
     */
    public Integer marcarVentasTarjetaNoCompletadas(Long cajaId, Long sucId, String motivo,
                                                    String observacion, Long usuarioId) {
        return service.marcarNoCompletadas(cajaId, sucursalService.exigirSucursalPropia(sucId),
                motivo, observacion, buscarUsuario(usuarioId));
    }

    /**
     * Deja UN cobro sin conciliar, con su motivo.
     *
     * <p>Es el caso real: de tres pendientes, dos tienen su cupon y el tercero se perdio. Marcar
     * los tres con el mismo motivo seria escribir dos mentiras para registrar una verdad.
     */
    public VentaTarjeta marcarVentaTarjetaNoCompletada(Long id, Long sucId, String motivo,
                                                       String observacion, Long usuarioId) {
        return service.marcarNoCompletada(id, sucursalService.exigirSucursalPropia(sucId),
                motivo, observacion, buscarUsuario(usuarioId));
    }

    /**
     * Devuelve un cobro de NO_COMPLETADO a PENDIENTE.
     *
     * <p>Sin esto, marcar la fila equivocada --de varias del mismo monto-- o dar por perdido un
     * cupon que despues aparece dejaba esa plata sin conciliar para siempre. Las columnas
     * `no_completado_*` NO se limpian: se conserva por que se habia marcado, y se suma quien
     * reabrio y cuando.
     */
    public VentaTarjeta reabrirVentaTarjeta(Long id, Long sucId, Long usuarioId) {
        return service.reabrir(id, sucursalService.exigirSucursalPropia(sucId),
                buscarUsuario(usuarioId));
    }

    /**
     * Quien esta actuando: marcando sin conciliar, o reabriendo.
     *
     * <p><b>Primero el JWT, y recien despues lo que dice el cliente.</b> Estas tres mutations
     * escriben {@code no_completado_por_id} / {@code reabierto_por_id}, que existen para una sola
     * cosa: que la decision tenga a quien preguntarle. Con el {@code usuarioId} tomado del
     * argumento, cualquier cliente autenticado podia marcar o reabrir un cobro <i>a nombre de
     * otro</i> --la pista de auditoria nacia falseable--. Hallazgo de la auditoria de seguridad
     * del 2026-09-21, y es una regresion propia de esta rama: antes no habia nada que suplantar.
     *
     * <p>El filial no guarda el usuario en el contexto, solo el nickname del token
     * ({@code JwtAuthenticationProvider} arma un {@code JwtUserDetails} con el), asi que se
     * resuelve por nickname. El argumento queda como respaldo para un contexto sin identidad
     * (tests, o una llamada interna), nunca para pisar al JWT.
     *
     * <p>Sin usuario la fila queda igual de marcada, pero sin a quien preguntarle. No se inventa uno.
     */
    private com.franco.dev.domain.personas.Usuario buscarUsuario(Long usuarioId) {
        String nickname = nicknameAutenticado();
        if (nickname != null) {
            java.util.Optional<com.franco.dev.domain.personas.Usuario> porToken =
                    usuarioService.findByNickname(nickname);
            log.debug("identidad: nickname del token='{}' → usuario {}", nickname,
                    porToken.isPresent() ? porToken.get().getId() : "NO ENCONTRADO");
            if (porToken.isPresent()) {
                if (usuarioId != null && !usuarioId.equals(porToken.get().getId())) {
                    log.warn("usuarioId {} del cliente no coincide con el del token ({}); manda el token",
                            usuarioId, porToken.get().getId());
                }
                return porToken.get();
            }
        }
        return usuarioId == null ? null : usuarioService.findById(usuarioId).orElse(null);
    }

    /** El nickname del JWT de esta request, o null si el contexto no trae identidad. */
    private static String nicknameAutenticado() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        log.debug("identidad en el contexto: auth={} autenticado={} principal={} hilo={}",
                auth == null ? null : auth.getClass().getSimpleName(),
                auth != null && auth.isAuthenticated(),
                auth == null || auth.getPrincipal() == null ? null : auth.getPrincipal().getClass().getSimpleName(),
                Thread.currentThread().getName());
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            String u = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            return u == null || u.trim().isEmpty() ? null : u;
        }
        return null;
    }

    /**
     * Imprime la sena de un cobro con tarjeta que quedo sin cupon.
     *
     * Se imprime aca --en el filial-- porque es donde vive la impresora y donde el PDV ya imprime el
     * ticket de la venta: `saveVenta` recibe el mismo `printerName` (de `configuracion-local.json`)
     * y el mismo `local`. No hay un segundo mecanismo de impresion ni hacia falta inventarlo.
     *
     * Devuelve false en vez de lanzar: para cuando esto corre, la venta ya se guardo y el cobro ya
     * se cobro. Lo unico que cambia si el papel no sale es que el cajero tiene que conciliar ese
     * cobro a mano, y eso se le avisa; una excepcion lo haria ver como si la venta hubiera fallado.
     */
    public Boolean imprimirSenaCupon(SenaCuponInput input, String printerName, String local) {
        if (input == null || input.getQr() == null) return false;
        SenaCuponDto dto = new SenaCuponDto();
        dto.setVentaId(input.getVentaId());
        dto.setVentaTarjetaId(input.getVentaTarjetaId());
        dto.setCajaId(input.getCajaId());
        dto.setCajero(input.getCajero());
        dto.setTerminal(input.getTerminal());
        dto.setMonto(input.getMonto());
        dto.setMonedaSimbolo(input.getMonedaSimbolo());
        dto.setDecimales(input.getDecimales());
        dto.setQr(input.getQr());
        return impresionService.printSenaCupon(dto, printerName, local);
    }
}
