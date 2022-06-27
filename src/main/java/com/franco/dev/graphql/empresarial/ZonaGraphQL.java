package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.Zona;
import com.franco.dev.graphql.empresarial.input.ZonaInput;
import com.franco.dev.service.empresarial.SectorService;
import com.franco.dev.service.empresarial.ZonaService;
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
public class ZonaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ZonaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SectorService sectorService;

    @Autowired
    private Environment env;

    public Optional<Zona> zona(Long id) {
        return service.findById(id);
    }

    public List<Zona> zonas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Zona> zonaesSearch(String texto) {
        return service.findByAll(texto);
    }


    public Zona saveZona(ZonaInput input) {
        ModelMapper m = new ModelMapper();
        Zona e = m.map(input, Zona.class);
        e.setSector(sectorService.findById(input.getSectorId()).orElse(null));
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteZona(Long id) {
        return service.deleteById(id);
    }

    public Long countZona() {
        return service.count();
    }

}
