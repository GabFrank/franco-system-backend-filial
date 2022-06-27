package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.graphql.configuraciones.input.LocalInput;
import com.franco.dev.service.configuracion.LocalService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LocalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private LocalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<Local> local(Long id) {
        return service.findById(id);
    }

    public List<Local> locales() {
        return service.findAll();
    }


    public Local saveLocal(LocalInput input) {
        ModelMapper m = new ModelMapper();
        Local e = m.map(input, Local.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteLocal(Long id) {
        return service.deleteById(id);
    }

    public Long countLocal() {
        return service.count();
    }


}
