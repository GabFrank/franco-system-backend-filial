package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.ProgramarPrecio;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.graphql.financiero.MovimientoCajaGraphQL;
import com.franco.dev.graphql.financiero.input.MovimientoCajaInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.ProgramarPrecioInput;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.operaciones.ProgramarPrecioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
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
public class ProgramarPrecioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProgramarPrecioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private PrecioPorSucursalService precioPorSucursalService;

    @Autowired
    private MovimientoCajaGraphQL movimientoCajaGraphQL;

    public Optional<ProgramarPrecio> programarPrecio(Long id) {return service.findById(id);}

    public List<ProgramarPrecio> programarPrecios(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public ProgramarPrecio saveProgramarPrecio(ProgramarPrecioInput input){
        ModelMapper m = new ModelMapper();
        ProgramarPrecio e = m.map(input, ProgramarPrecio.class);
        if(e.getUsuario()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(e.getPrecio()!=null) e.setPrecio(precioPorSucursalService.findById(input.getPrecioId()).orElse(null));
        ProgramarPrecio programarPrecio = service.save(e);
        return programarPrecio;
    }

    public Boolean deleteProgramarPrecio(Long id){
        return service.deleteById(id);
    }

    public Long countProgramarPrecio(){
        return service.count();
    }

}
