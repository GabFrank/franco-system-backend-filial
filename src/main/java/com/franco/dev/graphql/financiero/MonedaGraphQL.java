package com.franco.dev.graphql.financiero;

import com.franco.dev.service.financiero.MonedaModuloLector;
import com.franco.dev.service.seguridad.RolSesionService;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MonedaBilletes;
import com.franco.dev.graphql.financiero.input.MonedaInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.general.PaisService;
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
public class MonedaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RolSesionService rolSesionService;

    @Autowired
    private MonedaService service;
    @Autowired
    private MonedaModuloLector monedaModuloLector;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    public Optional<Moneda> moneda(Long id) {return service.findById(id);}

    public List<Moneda> monedas(int page, int size){
        return service.findAll2()   ;
    }


    public Moneda saveMoneda(MonedaInput input){
        rolSesionService.exigirAdmin();
        ModelMapper m = new ModelMapper();
        Moneda e = m.map(input, Moneda.class);
//        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
//        e.setPais(paisService.findById(input.getPaisId()).orElse(null));
        return service.save(e);
    }

    public List<Moneda> monedasSearch(String texto){
        return service.findByAll(texto);
    }

    public Boolean deleteMoneda(Long id){
        rolSesionService.exigirAdmin();
        return service.deleteById(id);
    }

    /**
     * La moneda en que opera el PDV de esta sucursal (modulo PDV de la moneda por modulo, leido del
     * espejo); sin configuracion, la de la empresa. Operacion aparte: un filial viejo no la tiene y el
     * escritorio sigue en la moneda de la empresa.
     */
    public Moneda monedaDelPdv() {
        return monedaModuloLector.pdv();
    }

    /** La moneda configurada para la factura de esta sucursal; null = la de la venta. */
    public Moneda monedaDeFacturacion() {
        return monedaModuloLector.configurada(MonedaModuloLector.FACTURACION).orElse(null);
    }

    public Long countMoneda(){
        return service.count();
    }


}
