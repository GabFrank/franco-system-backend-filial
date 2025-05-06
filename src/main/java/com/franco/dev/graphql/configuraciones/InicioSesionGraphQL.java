package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.graphql.configuraciones.input.InicioSesionInput;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
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


    public InicioSesion saveInicioSesion(InicioSesionInput input) {
        ModelMapper m = new ModelMapper();
        InicioSesion e = m.map(input, InicioSesion.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        if(input.getHoraInicio() != null) e.setHoraInicio(toDate(input.getHoraInicio()));
        if(input.getHoraFin() != null) e.setHoraFin(toDate(input.getHoraFin()));
        if(input.getCreadoEn() != null) e.setCreadoEn(toDate(input.getCreadoEn()));
        return service.saveAndSend(e, false);
    }

    public Boolean deleteInicioSesion(Long id) {
        return service.deleteById(id);
    }

    public Long countInicioSesion() {
        return service.count();
    }


}
