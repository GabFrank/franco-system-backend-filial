package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.graphql.financiero.input.PreGastoInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.PreGastoService;
import com.franco.dev.service.financiero.TipoGastoService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PreGastoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PreGastoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private TipoGastoService tipoGastoService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<PreGasto> preGasto(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<PreGasto> preGastos(String estado, Long sucId) {
        if (estado != null) {
            return service.findByEstado(estado);
        }
        return service.getRepository().findAll();
    }

    public List<PreGasto> preGastosPorSucursal(String estado, Long sucursalId) {
        return service.findByEstadoAndSucursal(estado, sucursalId);
    }

    public List<PreGasto> preGastosPorFuncionario(Long funcionarioId) {
        return service.findByFuncionario(funcionarioId);
    }

    public List<PreGasto> preGastosSearch(String texto, Long sucId) {
        return service.findByTexto(texto, sucId);
    }

    public PreGasto savePreGasto(PreGastoInput entity) throws GraphQLException {
        ModelMapper m = new ModelMapper();
        PreGasto e = m.map(entity, PreGasto.class);
        if (entity.getUsuarioId() != null) e.setUsuario(usuarioService.findById(entity.getUsuarioId()).orElse(null));
        if (entity.getFuncionarioId() != null) e.setFuncionario(funcionarioService.findById(entity.getFuncionarioId()).map(com.franco.dev.domain.personas.Funcionario::getPersona).orElse(null));
        if (entity.getAutorizadoPorId() != null) e.setAutorizadoPor(funcionarioService.findById(entity.getAutorizadoPorId()).map(com.franco.dev.domain.personas.Funcionario::getPersona).orElse(null));
        if (entity.getDelegadoAId() != null) e.setDelegadoA(funcionarioService.findById(entity.getDelegadoAId()).map(com.franco.dev.domain.personas.Funcionario::getPersona).orElse(null));
        if (entity.getTipoGastoId() != null) e.setTipoGasto(tipoGastoService.findById(entity.getTipoGastoId()).orElse(null));
        if (entity.getMonedaId() != null) e.setMoneda(monedaService.findById(entity.getMonedaId()).orElse(null));
        if (entity.getSucursalCajaId() != null) e.setSucursalCaja(sucursalService.findById(entity.getSucursalCajaId()).orElse(null));

        if (e.getEstado() == null) {
            e.setEstado(EstadoPreGasto.PENDIENTE);
        }

        return service.saveAndSend(e, false);
    }

    public PreGasto autorizarPreGasto(Long id, Long autorizadorId, Long sucId) {
        PreGasto e = service.findById(id).orElse(null);
        if (e != null) {
            e.setEstado(EstadoPreGasto.AUTORIZADO);
            if (autorizadorId != null) {
                e.setAutorizadoPor(funcionarioService.findById(autorizadorId).map(com.franco.dev.domain.personas.Funcionario::getPersona).orElse(null));
            }
            return service.saveAndSend(e, false);
        }
        return null;
    }

    public PreGasto rechazarPreGasto(Long id, String motivo, Long sucId) {
        PreGasto e = service.findById(id).orElse(null);
        if (e != null) {
            e.setEstado(EstadoPreGasto.RECHAZADO);
            e.setMotivoRechazo(motivo);
            return service.saveAndSend(e, false);
        }
        return null;
    }

    public PreGasto tramitarPreGasto(Long id, Long sucId) {
        PreGasto e = service.findById(id).orElse(null);
        if (e != null) {
            e.setEstado(EstadoPreGasto.TRAMITE);
            return service.saveAndSend(e, false);
        }
        return null;
    }

    public PreGasto completarPreGasto(Long id, Long sucId) {
        PreGasto e = service.findById(id).orElse(null);
        if (e != null) {
            e.setEstado(EstadoPreGasto.COMPLETADO);
            return service.saveAndSend(e, false);
        }
        return null;
    }

    public Boolean deletePreGasto(Long id, Long sucId) {
        return service.deleteById(id);
    }
}
