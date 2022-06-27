package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Entrada;
import com.franco.dev.domain.operaciones.Salida;
import com.franco.dev.graphql.operaciones.input.EntradaInput;
import com.franco.dev.graphql.operaciones.input.SalidaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.EntradaService;
import com.franco.dev.service.operaciones.SalidaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.reports.TicketReportService;
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
public class SalidaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SalidaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private TicketReportService ticketReportService;

    public Optional<Salida> salida(Long id) {return service.findById(id);}

    public List<Salida> salidas(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

//    public List<Salida> salidaSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Salida saveSalida(SalidaInput input){
        ModelMapper m = new ModelMapper();
        Salida e = m.map(input, Salida.class);
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getResponsableCargaId()!=null) e.setResponsableCarga(usuarioService.findById(input.getResponsableCargaId()).orElse(null));
        if(input.getSucursalId()!=null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));

        return service.save(e);
    }

    public Boolean deleteSalida(Long id){
        return service.deleteById(id);
    }

    public Long countSalida(){
        return service.count();
    }

    public List<Salida> salidaByFecha(String inicio, String fin){
        return service.findByDate(inicio, fin);
    }

    public Boolean finalizarSalida(Long id){
        return service.finalizarSalida(id);
    }
}
