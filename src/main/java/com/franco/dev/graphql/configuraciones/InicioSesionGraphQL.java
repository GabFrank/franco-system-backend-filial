package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.configuraciones.input.InicioSesionInput;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class InicioSesionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private InicioSesionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<InicioSesion> inicioSesion(Long id) {
        return service.findById(id);
    }

    public List<InicioSesion> inicioSesiones() {
        return service.findAll();
    }

    public Page<InicioSesion> inicioSesionListPorUsuarioIdAndAbierto(Long id, Long sucId, Integer page, Integer size){
        Pageable pageable = PageRequest.of(page, size);
        return service.findByUsuarioIdAndHoraFinIsNul(id, sucId, pageable);
    }


    /**
     * La sucursal la decide el servidor, no el cliente: un cliente que manda la sucursal 0 (central)
     * generaba filas (id, 0) que chocan en central al replicar y cortan la suscripcion (issue #77).
     * Solo se actualizan sesiones de esta sucursal, para no reescribir ni republicar filas ajenas.
     */
    public InicioSesion saveInicioSesion(InicioSesionInput input) {
        Sucursal sucursal = sucursalPropia();
        InicioSesion e;
        if (input.getId() == null) {
            e = new InicioSesion();
        } else {
            Optional<InicioSesion> existente = service.findByIdAndSucursalId(input.getId(), sucursal.getId());
            if (!existente.isPresent()) {
                // Sesion ajena o legacy (id, 0): no se toca. Se devuelve null y no un error porque los
                // desktops sin actualizar esperan el cierre sin manejar errores y el logout se colgaria.
                return null;
            }
            e = existente.get();
        }
        e.setSucursal(sucursal);
        // Solo lo que llega: el input no trae todos los campos y antes se pisaban con null.
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getTipoDespositivo() != null) e.setTipoDespositivo(input.getTipoDespositivo());
        if (input.getIdDispositivo() != null) e.setIdDispositivo(input.getIdDispositivo());
        if (input.getToken() != null) e.setToken(input.getToken());
        if(input.getHoraInicio() != null) e.setHoraInicio(toDate(input.getHoraInicio()));
        if(input.getHoraFin() != null) e.setHoraFin(toDate(input.getHoraFin()));
        if(input.getCreadoEn() != null) e.setCreadoEn(toDate(input.getCreadoEn()));
        return service.saveAndSend(e, false);
    }

    private Sucursal sucursalPropia() {
        Sucursal sucursal;
        try {
            sucursal = sucursalService.sucursalActual();
        } catch (NumberFormatException ex) {
            sucursal = null;
        }
        if (sucursal == null || sucursal.getId() == null || sucursal.getId() == 0L) {
            throw new GraphQLException("Servidor sin sucursal configurada");
        }
        return sucursal;
    }

    public Boolean deleteInicioSesion(Long id) {
        return service.deleteById(id);
    }

    public Long countInicioSesion() {
        return service.count();
    }

    public Boolean notificarInicioSesion(Long usuarioId) {
        if (usuarioId == null) {
            return false;
        }
        return usuarioService.findById(usuarioId).isPresent();
    }

    public Boolean actualizarTokenFcm(String tokenFcm) {
        return true;
    }

}
