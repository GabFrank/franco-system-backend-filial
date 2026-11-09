package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.graphql.financiero.input.GastoInput;
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
        auditorUsuarioId.verificar("GastoGraphQL.saveGasto", input.getUsuarioId());
        if (input.getId() == null) {
            String observacion = input.getObservacion() != null ? input.getObservacion().trim() : "";
            if (observacion.isEmpty()) {
                throw new GraphQLException("La observación es obligatoria para registrar un gasto.");
            }
            input.setObservacion(observacion);
        }
        ModelMapper m = new ModelMapper();
        Gasto e = m.map(input, Gasto.class);

        // Sin moneda, el retiro va en 0: saveAndSend compara los montos antes de guardar.
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
        Gasto gasto = service.saveAndSend(e, false);
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

//    public List<Gasto> gastosSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteGasto(Long id, Long sucId) {
        return service.deleteById(id);
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
