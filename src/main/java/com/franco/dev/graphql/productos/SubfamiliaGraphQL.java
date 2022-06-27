package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.graphql.productos.input.SubfamiliaInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.productos.SubFamiliaService;
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
public class SubfamiliaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SubFamiliaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FamiliaService familiaService;

    public Optional<Subfamilia> subfamilia(Long id) {return service.findById(id);}

    public List<Subfamilia> subfamiliaSearch(String texto) {return service.findByDescripcion(texto);}

//    public List<Subfamilia> subfamilias(int page, int size){
//        Pageable pageable = PageRequest.of(page,size);
//        return service.findAll(pageable);
//    }

    public Subfamilia saveSubfamilia(SubfamiliaInput input){
        ModelMapper m = new ModelMapper();
        Subfamilia e = m.map(input, Subfamilia.class);
        if(input.getUsuarioId()!=null){
            e.setUsuarioId(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getFamiliaId()!=null){
            e.setFamilia(familiaService.findById((input.getFamiliaId())).orElse(null));

        }
        if(input.getSubfamiliaId()!=null){
            e.setSubfamilia(service.findById((input.getSubfamiliaId())).orElse(null));
        }
        return service.save(e);
    }

    public Subfamilia updateSubfamilia(Long id, SubfamiliaInput input){
        ModelMapper m = new ModelMapper();
        Subfamilia p = service.getOne(id);
        p = m.map(input, Subfamilia.class);
        return service.save(p);
    }

    public Boolean deleteSubfamilia(Long id){
        return service.deleteById(id);
    }

    public Long countSubfamilia(){
        return service.count();
    }

    public List<Subfamilia> subfamilias(int page, int size){
        return service.findBySubfamiliaIsNull();
    }
}
