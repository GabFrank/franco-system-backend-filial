package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Necesidad;
import com.franco.dev.graphql.operaciones.input.NecesidadInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.NecesidadService;
import com.franco.dev.service.personas.UsuarioService;
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
public class NecesidadGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NecesidadService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<Necesidad> necesidad(Long id) {return service.findById(id);}

    public List<Necesidad> necesidades(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Necesidad> necesidadesSearch(String texto){
        return service.findByAll(texto);
    }

    public List<Necesidad> necesidadesPorFecha(String start, String end){
        if (end == null){
            end = start;
        }
        return service.findByDate(start, end);
    }

    public Necesidad saveNecesidad(NecesidadInput input){
        ModelMapper m = new ModelMapper();
        Necesidad e = m.map(input, Necesidad.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteNecesidad(Long id){
        return service.deleteById(id);
    }

    public Long countNecesidad(){
        return service.count();
    }

}
