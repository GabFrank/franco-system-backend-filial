package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.domain.operaciones.SalidaItem;
import com.franco.dev.graphql.operaciones.input.EntradaItemInput;
import com.franco.dev.graphql.operaciones.input.SalidaItemInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.EntradaItemService;
import com.franco.dev.service.operaciones.EntradaService;
import com.franco.dev.service.operaciones.SalidaItemService;
import com.franco.dev.service.operaciones.SalidaService;
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
public class SalidaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SalidaItemService service;

    @Autowired
    private SalidaService salidaService;

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

    public Optional<SalidaItem> salidaItem(Long id) {return service.findById(id);}

    public List<SalidaItem> salidaItems(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<SalidaItem> salidaItemsPorSalidaId(Long id){
        return service.findBySalidaId(id);
    }

    public SalidaItem saveSalidaItem(SalidaItemInput input){
        ModelMapper m = new ModelMapper();
        SalidaItem e = m.map(input, SalidaItem.class);
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getSalidaId()!=null) e.setSalida(salidaService.findById(input.getSalidaId()).orElse(null));
        if(input.getProductoId()!=null) e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        if(input.getPresentacionId()!=null) e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteSalidaItem(Long id){
        return service.deleteById(id);
    }

    public Long countSalidaItem(){
        return service.count();
    }


}
