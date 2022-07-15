package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.InventarioEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.graphql.operaciones.input.InventarioInput;
import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.rabbit.enums.TipoAccion;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.InventarioProductoItemService;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.operaciones.InventarioService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.reports.TicketReportService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class InventarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private InventarioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private TicketReportService ticketReportService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private Environment env;


    public Optional<Inventario> inventario(Long id) {
        return service.findById(id);
    }

    public List<Inventario> inventarioList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Inventario> inventarioPorUsuario(Long id) {
        return service.findByUsuario(id);
    }


//    public List<Inventario> inventarioSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Inventario saveInventario(InventarioInput input) {
        ModelMapper m = new ModelMapper();
        Inventario e = m.map(input, Inventario.class);
        if(input.getFechaInicio()!=null) e.setFechaInicio(toDate(input.getFechaInicio()));
        if(input.getFechaFin()!=null) e.setFechaFin(toDate(input.getFechaFin()));
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e = service.save(e);
        return e;
    }

    public Boolean deleteInventario(Long id) {
        Boolean ok = false;
        Inventario i = service.findById(id).orElse(null);
        if(i!=null) {
            ok = service.deleteById(id);
            propagacionService.deleteEntidad(i, TipoEntidad.INVENTARIO);
        }
        return ok;
    }

    public Long countInventario() {
        return service.count();
    }

    public List<Inventario> inventarioPorFecha(String inicio, String fin) {
        return service.findByDate(inicio, fin);
    }

}
