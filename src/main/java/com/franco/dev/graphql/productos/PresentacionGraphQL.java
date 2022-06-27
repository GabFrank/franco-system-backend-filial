package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.graphql.productos.input.PresentacionInput;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.utils.ImageService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Component
public class PresentacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    public static final Logger log = Logger.getLogger(String.valueOf(PresentacionGraphQL.class));

    @Autowired
    private PresentacionService service;

    @Autowired
    private ImageService imageService;

    public Optional<Presentacion> presentacion(Long id) {return service.findById(id);}

    public List<Presentacion> presentacionSearch(String texto) {return service.findByAll(texto);}

    public List<Presentacion> presentaciones(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Presentacion savePresentacion(PresentacionInput input) throws GraphQLException{
        ModelMapper m = new ModelMapper();
        Boolean isPrincipalInUse = false;
        if(input.getPrincipal()){
            List<Presentacion> presentacionList = service.findByProductoId(input.getProductoId());
            for(Presentacion p : presentacionList){
                if(input.getId()==null){
                    if(p.getPrincipal()){
                        throw new GraphQLException("Ya existe una presentación principal");
                    }
                } else if(input.getId()!=p.getId() && p.getPrincipal()){
                    throw new GraphQLException("Ya existe una presentación principal");
                }
            }
        }

        Presentacion e = m.map(input, Presentacion.class);
        return service.save(e);

    }

    public Presentacion updatePresentacion(Long id, PresentacionInput input){
        ModelMapper m = new ModelMapper();
        Presentacion p = service.getOne(id);
        p = m.map(input, Presentacion.class);
        return service.save(p);
    }

    public List<Presentacion> presentacionesPorProductoId(Long id){
        return service.findByProductoId(id);
    }

    public Boolean deletePresentacion(Long id){
        return service.deleteById(id);
    }

    public Long countPresentacion(){
        return service.count();
    }

    public Boolean saveImagenPresentacion(String image, String filename) throws IOException {
        log.info("intrando en el image save");
        return imageService.saveImageToPath(image, filename, true);
    }

}
