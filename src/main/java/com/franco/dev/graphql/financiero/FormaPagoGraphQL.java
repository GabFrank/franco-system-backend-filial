package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.graphql.financiero.input.FormaPagoInput;
import com.franco.dev.graphql.financiero.input.MonedaInput;
import com.franco.dev.service.financiero.CuentaBancariaService;
import com.franco.dev.service.financiero.FormaPagoService;
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
public class FormaPagoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FormaPagoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private CuentaBancariaService cuentaBancariaService;

    public Optional<FormaPago> formaPago(Long id) {return service.findById(id);}

    public List<FormaPago> formasPago(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public FormaPago saveFormaPago(FormaPagoInput input){
        ModelMapper m = new ModelMapper();
        FormaPago e = m.map(input, FormaPago.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getCuentaBancariaId()!=null){
            e.setCuentaBancaria(cuentaBancariaService.findById(input.getCuentaBancariaId()).orElse(null));
        }
        return service.save(e);
    }

    public Boolean deleteFormaPago(Long id){
        return service.deleteById(id);
    }

    public Long countFormaPago(){
        return service.count();
    }


}
