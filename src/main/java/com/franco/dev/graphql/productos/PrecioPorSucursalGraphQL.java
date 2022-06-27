package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.graphql.productos.input.CodigoInput;
import com.franco.dev.graphql.productos.input.PrecioPorSucursalInput;
import com.franco.dev.security.AdminSecured;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.*;
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
public class PrecioPorSucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PrecioPorSucursalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private TipoPrecioService tipoPrecioService;

    @Autowired
    private Environment env;

    public Optional<PrecioPorSucursal> precioPorSucursal(Long id) {return service.findById(id);}

    public List<PrecioPorSucursal> precioPorSucursalPorPresentacionId(Long id) {return service.findByPresentacionId(id);}

    public List<PrecioPorSucursal> preciosPorSucursalPorSucursalId(Long id){ return service.findBySucursalId(id);}

    public List<PrecioPorSucursal> preciosPorSucursal(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public PrecioPorSucursal savePrecioPorSucursal(PrecioPorSucursalInput input){
        ModelMapper m = new ModelMapper();
        PrecioPorSucursal e = m.map(input, PrecioPorSucursal.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getPresentacionId()!=null){
            e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        }
        if(input.getTipoPrecioId()!=null){
            e.setTipoPrecio(tipoPrecioService.findById(input.getTipoPrecioId()).orElse(null));
        }
        input.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        return service.save(e);
    }

    public Boolean deletePrecioPorSucursal(Long id){
        return service.deleteById(id);
    }

    public Long countPrecioPorSucursal(){
        return service.count();
    }

}
