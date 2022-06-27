package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.graphql.operaciones.input.TransferenciaItemInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class TransferenciaItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TransferenciaItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private TransferenciaService transferenciaService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<TransferenciaItem> transferenciaItem(Long id) {return service.findById(id);}

    public List<TransferenciaItem> transferenciaItems(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<TransferenciaItem> transferenciaItemsPorTransferenciaId(Long id){
        return service.findByTransferenciaId(id);
    }

    public TransferenciaItem saveTransferenciaItem(TransferenciaItemInput input){
        ModelMapper m = new ModelMapper();
        TransferenciaItem e = m.map(input, TransferenciaItem.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setTransferencia(transferenciaService.findById(input.getTransferenciaId()).orElse(null));
        if(input.getVencimientoPreTransferencia()!=null) e.setVencimientoPreTransferencia(toDate(input.getVencimientoPreTransferencia()));
        if(input.getVencimientoPreparacion()!=null) e.setVencimientoPreparacion(toDate(input.getVencimientoPreparacion()));
        if(input.getVencimientoTransporte()!=null) e.setVencimientoTransporte(toDate(input.getVencimientoTransporte()));
        if(input.getVencimientoRecepcion()!=null) e.setVencimientoRecepcion(toDate(input.getVencimientoRecepcion()));
        if(input.getPresentacionPreTransferenciaId()!=null)e.setPresentacionPreTransferencia(presentacionService.findById(input.getPresentacionPreTransferenciaId()).orElse(null));
        if(input.getPresentacionPreparacionId()!=null)e.setPresentacionPreparacion(presentacionService.findById(input.getPresentacionPreparacionId()    ).orElse(null));
        if(input.getPresentacionTransporteId()!=null)e.setPresentacionTransporte(presentacionService.findById(input.getPresentacionTransporteId()).orElse(null));
        if(input.getPresentacionRecepcionId()!=null)e.setPresentacionRecepcion(presentacionService.findById(input.getPresentacionRecepcionId()).orElse(null));
        e = service.save(e);
        propagacionService.propagarEntidad(e, TipoEntidad.TRANSFERENCIA_ITEM);
        return e;
    }

    public Boolean deleteTransferenciaItem(Long id){
        return service.deleteById(id);
    }
}
