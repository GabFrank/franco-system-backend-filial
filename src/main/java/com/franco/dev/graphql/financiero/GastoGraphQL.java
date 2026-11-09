package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.graphql.financiero.input.GastoInput;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.seguridad.RolSesionService;
import com.franco.dev.service.financiero.TipoGastoService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.impresion.dto.GastoDto;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

@Component
public class GastoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private AuditorUsuarioId auditorUsuarioId;

    @Autowired
    private GastoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private GastoDetalleGraphQL gastoDetalleGraphQL;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private RolSesionService rolSesionService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private TipoGastoService tipoGastoService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    public Optional<Gasto> gasto(Long id, Long sucId) {
        Optional<Gasto> gasto = service.findById(id);
        gasto.ifPresent(g -> pdvCajaService.exigirAccesoARegistroDeCaja(g.getCaja()));
        return gasto;
    }

    public List<Gasto> gastos(int page, int size, Long sucId) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Gasto> gastosPorCajaId(Long id, Long sucId) {
        pdvCajaService.exigirAccesoACaja(id);
        return service.findByCajaId(id);
    }

    public List<Gasto> gastosPorFecha(String inicio, String fin, Long sucId) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        return service.findByDate(inicio, fin);
    }

    public Gasto saveGasto(GastoInput input, String printerName, String local, Boolean imprimirEnCliente)
            throws GraphQLException {
        // Editar y finalizar reenvian al autor original a proposito. Edicion solo si el gasto existe: un
        // id inventado no puede esquivar la firma (issue #142).
        boolean edicion = input.getId() != null && service.findById(input.getId()).isPresent();
        if (!edicion) {
            PdvCaja caja = input.getCajaId() != null ? pdvCajaService.findById(input.getCajaId()).orElse(null) : null;
            if (caja == null) {
                throw new GraphQLException("Caja no encontrada");
            }
            pdvCajaService.controlarAccesoEnAlta("GastoGraphQL.saveGasto", caja);
        }
        if (edicion) {
            auditorUsuarioId.verificar("GastoGraphQL.saveGasto", input.getUsuarioId());
        } else {
            input.setUsuarioId(auditorUsuarioId.firmante("GastoGraphQL.saveGasto", input.getUsuarioId(),
                    input.getCajaId()));
        }
        if (input.getId() == null) {
            String observacion = input.getObservacion() != null ? input.getObservacion().trim() : "";
            if (observacion.isEmpty()) {
                throw new GraphQLException("La observación es obligatoria para registrar un gasto.");
            }
            input.setObservacion(observacion);
        }
        ModelMapper m = new ModelMapper();
        Gasto e = m.map(input, Gasto.class);

        // Sin moneda, el retiro va en 0: guardarGastoNuevo compara los montos antes de guardar.
        e.setRetiroGs(PdvCajaService.cero(input.getRetiroGs()));
        e.setRetiroRs(PdvCajaService.cero(input.getRetiroRs()));
        e.setRetiroDs(PdvCajaService.cero(input.getRetiroDs()));
        if (input.getVueltoGs() != null) {
            e.setVueltoGs(input.getVueltoGs());
        } else {
            e.setVueltoGs(0.0);
        }
        if (input.getVueltoRs() != null) {
            e.setVueltoRs(input.getVueltoRs());
        } else {
            e.setVueltoRs(0.0);
        }
        if (input.getVueltoDs() != null) {
            e.setVueltoDs(input.getVueltoDs());
        } else {
            e.setVueltoDs(0.0);
        }
        e.setFinalizado(true);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCajaId() != null) e.setCaja(pdvCajaService.findById(input.getCajaId()).orElse(null));
        if (input.getAutorizadoPorId() != null)
            e.setAutorizadoPor(funcionarioService.findById(input.getAutorizadoPorId()).orElse(null));
        if (input.getResponsableId() != null)
            e.setResponsable(funcionarioService.findById(input.getResponsableId()).orElse(null));
        if (input.getTipoGastoId() != null)
            e.setTipoGasto(tipoGastoService.findById(input.getTipoGastoId()).orElse(null));
        if (input.getPreGastoId() != null || input.getPreGastoSucursalId() != null) {
            if (input.getPreGastoId() == null || input.getPreGastoSucursalId() == null) {
                throw new GraphQLException("Debe enviar preGastoId y preGastoSucursalId para asociar el gasto.");
            }
            e.setPreGastoId(input.getPreGastoId());
            e.setPreGastoSucursalId(input.getPreGastoSucursalId());
        } else {
            e.setPreGastoId(null);
            e.setPreGastoSucursalId(null);
        }
        // Mismo criterio que la firma: un id inventado no esquiva el control de efectivo.
        Gasto gasto = edicion ? guardarEdicion(e) : guardarGastoNuevo(e);
        GastoDto gastoDto = new GastoDto();
        // imprimirEnCliente: el desktop pide el ticket despues con ticketEscpos(GASTO) y lo imprime el.
        if (gasto != null && input.getFinalizado() != true && !Boolean.TRUE.equals(imprimirEnCliente)) {
            gastoDto.setId(gasto.getId());
            gastoDto.setFecha(gasto.getCreadoEn());
            gastoDto.setUsuario(gasto.getUsuario());
            gastoDto.setResponsable(gasto.getResponsable());
            gastoDto.setAutorizadoPor(gasto.getAutorizadoPor());
            gastoDto.setTipoGasto(gasto.getTipoGasto());
            gastoDto.setObservacion(gasto.getObservacion());
            gastoDto.setRetiroGs(input.getRetiroGs());
            gastoDto.setRetiroRs(input.getRetiroRs());
            gastoDto.setRetiroDs(input.getRetiroDs());
            gastoDto.setVueltoGs(input.getVueltoGs());
            gastoDto.setVueltoRs(input.getVueltoRs());
            gastoDto.setVueltoDs(input.getVueltoDs());
            gastoDto.setCajaId(gasto.getCaja() != null ? gasto.getCaja().getId() : null);
            impresionService.printGasto(gastoDto, printerName, local);
        }
        return gasto;
    }

    /**
     * Alta con control de efectivo: bloqueo de la caja, saldo con la cuenta del cierre y guardado en una
     * transaccion, sin la impresion (un error al imprimir no deshace el gasto). Issue #80.
     */
    private Gasto guardarGastoNuevo(Gasto e) {
        if (e.getCaja() == null) {
            throw new GraphQLException("Caja no encontrada");
        }
        return new TransactionTemplate(transactionManager).execute(status -> {
            pdvCajaService.bloquearSaldoCaja(e.getCaja().getId());
            PdvCajaBalanceDto balance = pdvCajaService.generarBalance(e.getCaja());
            if (PdvCajaService.superaSaldo(balance, "GUARANI", e.getRetiroGs())) {
                throw new GraphQLException("El valor de gasto es mayor al total en caja: Guaraníes");
            }
            if (PdvCajaService.superaSaldo(balance, "REAL", e.getRetiroRs())) {
                throw new GraphQLException("El valor de gasto es mayor al total en caja: Reales");
            }
            if (PdvCajaService.superaSaldo(balance, "DOLAR", e.getRetiroDs())) {
                throw new GraphQLException("El valor de gasto es mayor al total en caja: Dolares");
            }
            Gasto g = service.saveAndSend(e, false);
            if (g != null) emitirGasto("gasto.creado", g);
            return g;
        });
    }

    @Autowired
    private com.franco.dev.eventos.EventoEmisor eventoEmisor;

    /** gasto.creado / editado / eliminado (fase 2, #246), en la TX del guardado; la impresion queda afuera. */
    void emitirGasto(String tipo, Gasto g) {
        eventoEmisor.emitirProyeccion(tipo, "gasto.eliminado".equals(tipo) ? "media" : "baja",
                g.getUsuario() != null ? g.getUsuario().getId() : null,
                "gasto/" + g.getSucursalId() + "/" + g.getId(),
                () -> {
                    java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
                    d.put("id", g.getId());
                    if (g.getCaja() != null) d.put("caja", com.franco.dev.eventos.EventosVenta.mapa("id", g.getCaja().getId()));
                    d.put("sucursalId", g.getSucursalId());
                    if (g.getTipoGasto() != null) {
                        java.util.Map<String, Object> t = com.franco.dev.eventos.EventosVenta.mapa("id", g.getTipoGasto().getId());
                        t.put("descripcion", g.getTipoGasto().getDescripcion());
                        d.put("tipoGasto", t);
                    }
                    d.put("valorGs", g.getRetiroGs());
                    d.put("valorRs", g.getRetiroRs());
                    d.put("valorDs", g.getRetiroDs());
                    d.put("descripcion", g.getObservacion());
                    if (g.getAutorizadoPor() != null && g.getAutorizadoPor().getPersona() != null)
                        d.put("autorizadoPor", com.franco.dev.eventos.EventosVenta.mapa("nickname", g.getAutorizadoPor().getPersona().getNombre()));
                    if (g.getUsuario() != null) d.put("usuario", com.franco.dev.eventos.EventosVenta.mapa("id", g.getUsuario().getId()));
                    d.put("creadoEn", g.getCreadoEn() != null ? g.getCreadoEn().toString() : null);
                    return d;
                },
                () -> {
                    java.util.Map<String, Object> a = new java.util.LinkedHashMap<>();
                    a.put("caja_id", g.getCaja() != null ? g.getCaja().getId() : null);
                    a.put("tipo_gasto_id", g.getTipoGasto() != null ? g.getTipoGasto().getId() : null);
                    a.put("monto_gs", g.getRetiroGs());
                    return a;
                });
    }

//    public List<Gasto> gastosSearch(String texto){
//        return service.findByAll(texto);
//    }

    /** Edicion en TX con su aviso (fase 2, #246): antes el guardado iba solo. */
    private Gasto guardarEdicion(Gasto e) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Gasto g = service.saveAndSend(e, false);
            if (g != null) emitirGasto("gasto.editado", g);
            return g;
        });
    }

    public Boolean deleteGasto(Long id, Long sucId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Gasto g = service.findById(id).orElse(null);
            if (g == null) return false;
            Boolean ok = service.deleteById(id);
            if (Boolean.TRUE.equals(ok)) emitirGasto("gasto.eliminado", g);
            return ok;
        });
    }

    public Long countGasto() {
        return service.count();
    }

    public Boolean reimprimirGasto(Long id, String printerName, Long sucId) {
        Gasto gasto = service.findById(id).orElseThrow(() -> new GraphQLException("El gasto " + id + " no existe"));
        // Fuera del try: un rechazo es un error, no un "intente de nuevo".
        pdvCajaService.exigirAccesoARegistroDeCaja(gasto.getCaja());
        try {
            GastoDto gastoDto = new GastoDto();
            gastoDto.setId(gasto.getId());
            gastoDto.setFecha(gasto.getCreadoEn());
            gastoDto.setUsuario(gasto.getUsuario());
            gastoDto.setResponsable(gasto.getResponsable());
            gastoDto.setAutorizadoPor(gasto.getAutorizadoPor());
            gastoDto.setTipoGasto(gasto.getTipoGasto());
            gastoDto.setObservacion(gasto.getObservacion());
            gastoDto.setRetiroGs(gasto.getRetiroGs());
            gastoDto.setRetiroRs(gasto.getRetiroRs());
            gastoDto.setRetiroDs(gasto.getRetiroDs());
            gastoDto.setVueltoGs(gasto.getVueltoGs());
            gastoDto.setVueltoRs(gasto.getVueltoRs());
            gastoDto.setVueltoDs(gasto.getVueltoDs());
            gastoDto.setCajaId(gasto.getCaja() != null ? gasto.getCaja().getId() : null);
            gastoDto.setReimpresion(true);
            return impresionService.printGasto(gastoDto, printerName, null, null, true);
        } catch (Exception e) {
            return false;
        }
    }

    public Gasto saveVueltoGasto(Long id, Double valorGs, Double valorRs, Double valorDs, Long sucId) throws GraphQLException {
        Gasto gasto = service.findById(id).orElse(null);
        if (gasto == null) {
            throw new GraphQLException("Gasto no encontrado");
        } else {
            gasto.setSucursalVuelto(sucursalService.sucursalActual());
            gasto.setVueltoGs(valorGs);
            gasto.setVueltoRs(valorRs);
            gasto.setVueltoDs(valorDs);
            return service.saveAndSend(gasto, false);
        }
    }


}
