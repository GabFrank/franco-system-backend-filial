package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuracion.Actualizacion;
import com.franco.dev.domain.configuracion.enums.TipoActualizacion;
import com.franco.dev.graphql.configuraciones.input.ActualizacionInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.configuracion.ActualizacionService;
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
public class ActualizacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ActualizacionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<Actualizacion> actualizacion(Long id) {return service.findById(id);}

    public List<Actualizacion> actualizaciones(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Actualizacion ultimaActualizacion(TipoActualizacion tipo){
        return service.findLast(tipo);
    }


    public Actualizacion saveActualizacion(ActualizacionInput input){
        ModelMapper m = new ModelMapper();
        Actualizacion e = m.map(input, Actualizacion.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e = service.save(e);
        propagacionService.propagarEntidad(e, TipoEntidad.CARGO);
        return e;
    }

    public Boolean deleteActualizacion(Long id){
//        Boolean ok = service.deleteById(id);
//        if(ok) propagacionService.eliminarEntidad(id, TipoEntidad.CARGO);
        return true;
    }

    public Long countActualizacion(){
        return service.count();
    }

}
