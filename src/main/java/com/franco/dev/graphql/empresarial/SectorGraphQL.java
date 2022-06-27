package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.Sector;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.empresarial.input.SectorInput;
import com.franco.dev.graphql.empresarial.input.SucursalInput;
import com.franco.dev.service.empresarial.SectorService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SectorGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SectorService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private Environment env;

    public Optional<Sector> sector(Long id) {return service.findById(id);}

    public List<Sector> sectores(Long id){
        return service.findAll2();
    }

    public List<Sector> sectoresSearch(String texto){
        return service.findByAll(texto);
    }

    public Sector saveSector(SectorInput input){
        ModelMapper m = new ModelMapper();
        Sector e = m.map(input, Sector.class);
        e.setSucursal(sucursalService.findById(input.getId()).orElse(null));
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteSector(Long id){
        return service.deleteById(id);
    }

    public Long countSector(){
        return service.count();
    }

}
