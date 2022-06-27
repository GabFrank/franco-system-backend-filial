package com.franco.dev.graphql.personas;

import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.general.enums.DiasSemana;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.ProveedorDiasVisita;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.graphql.personas.input.ProveedorDiasVisitaInput;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.personas.*;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ProveedorDiasVisitaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProveedorDiasVisitaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private ContactoService contactoService;

    public Optional<ProveedorDiasVisita> proveedorDiasVisita(Long id) {return service.findById(id);}

    public List<ProveedorDiasVisita> proveedorDiasVisitas(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<DiasSemana> proveedorDiasVisitaPorProveedor(Long id){
       return service.findByProveedorId(id);
    }

    public List<Proveedor> proveedorDiasVisitaPorDia(DiasSemana dia){
        return service.findByDia(dia);
    }

    public ProveedorDiasVisita saveProveedorDiasVisita(ProveedorDiasVisitaInput input){
        ModelMapper m = new ModelMapper();
        ProveedorDiasVisita e = m.map(input, ProveedorDiasVisita.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setProveedor(proveedorService.findById(input.getProveedorId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteProveedorDiasVisita(Long id){
        return service.deleteById(id);
    }

    public Long countProveedorDiasVisita(){
        return service.count();
    }

//    public List<ProveedorDiasVisita> proveedorDiasVisitaPorProveedorNombre(String texto){
//        return service.findByProveedorNombre(texto);
//    }

}
