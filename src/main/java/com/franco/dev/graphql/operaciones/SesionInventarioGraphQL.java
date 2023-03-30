package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.SesionInventario;
import com.franco.dev.graphql.operaciones.input.SesionInventarioInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.SesionInventarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
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
public class SesionInventarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SesionInventarioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PropagacionService propagacionService;


    public Optional<SesionInventario> sesionInventario(Long id) {
        return service.findById(id);
    }

    public List<SesionInventario> sesionInventarioList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

//    public List<SesionInventario> sesionInventarioSearch(String texto){
//        return service.findByAll(texto);
//    }

//    public List<SesionInventarioionInventario> sesionInventarioPorUsuario(Long id) {
//        return service.findByUsuario(id);
//    }


    public SesionInventario saveSesionInventario(SesionInventarioInput input) {
        ModelMapper m = new ModelMapper();
        SesionInventario e = m.map(input, SesionInventario.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e = service.save(e);
        return e;
    }

    public Boolean deleteSesionInventario(Long id) {
        Boolean ok = false;
        SesionInventario i = service.findById(id).orElse(null);
        if (i != null) {
            ok = service.deleteById(id);
        }
        return ok;
    }

    public Long countSesionInventario() {
        return service.count();
    }

}
