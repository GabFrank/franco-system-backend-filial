package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Entrada;
import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.operaciones.input.EntradaInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.print.operaciones.MovimientoPrintService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.EntradaService;
import com.franco.dev.service.operaciones.VentaService;
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
public class EntradaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private EntradaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private TicketReportService ticketReportService;


    public Optional<Entrada> entrada(Long id) {return service.findById(id);}

    public List<Entrada> entradas(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

//    public List<Entrada> entradaSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Entrada saveEntrada(EntradaInput input){
        ModelMapper m = new ModelMapper();
        Entrada e = m.map(input, Entrada.class);
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getResponsableCargaId()!=null) e.setResponsableCarga(usuarioService.findById(input.getResponsableCargaId()).orElse(null));
        if(input.getSucursalId()!=null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));

        return service.save(e);
    }

    public Boolean deleteEntrada(Long id){
        return service.deleteById(id);
    }

    public Long countEntrada(){
        return service.count();
    }

    public List<Entrada> entradaByFecha(String inicio, String fin){
        return service.findByDate(inicio, fin);
    }

    public Boolean finalizarEntrada(Long id){
        return service.finalizarEntrada(id);
    }

    public Boolean imprimirEntrada(Long id){
        return service.imprimirEntrada(id);
    }

}
