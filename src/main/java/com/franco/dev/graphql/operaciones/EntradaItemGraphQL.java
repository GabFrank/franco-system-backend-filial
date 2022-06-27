package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.graphql.operaciones.input.EntradaItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.EntradaItemService;
import com.franco.dev.service.operaciones.EntradaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
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
public class EntradaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private EntradaItemService service;

    @Autowired
    private EntradaService entradaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private TicketReportService ticketReportService;

    public Optional<EntradaItem> entradaItem(Long id) {return service.findById(id);}

    public List<EntradaItem> entradaItems(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<EntradaItem> entradaItemsPorEntradaId(Long id){
        return service.findByEntradaId(id);
    }

    public EntradaItem saveEntradaItem(EntradaItemInput input){
        ModelMapper m = new ModelMapper();
        EntradaItem e = m.map(input, EntradaItem.class);
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getEntradaId()!=null) e.setEntrada(entradaService.findById(input.getEntradaId()).orElse(null));
        if(input.getProductoId()!=null) e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        if(input.getPresentacionId()!=null) e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteEntradaItem(Long id){
        return service.deleteById(id);
    }

    public Long countEntradaItem(){
        return service.count();
    }


}
