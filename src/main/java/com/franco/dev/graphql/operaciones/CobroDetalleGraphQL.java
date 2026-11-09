package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.operaciones.CompraService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class CobroDetalleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private AuditorUsuarioId auditorUsuarioId;

    @Autowired
    private CobroDetalleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MonedaService monedaService;
    @Autowired
    private FormaPagoService formaPagoService;
    @Autowired
    private CobroService cobroService;


    @Autowired
    private CompraService compraService;

    public Optional<CobroDetalle> cobroDetalle(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<CobroDetalle> cobroDetallePorCobroId(Long id, Long sucId) {
        return service.findByCobroId(id);
    }

    public List<CobroDetalle> cobroDetalleList(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    /**
     * Mutation directa: editar un cobro ya guardado avisa venta.cobro_editado en la misma TX (fase 2,
     * #246). El alta y lo que guardan saveVenta / saveVentaDelivery van por guardarDetalle, sin aviso.
     */
    @org.springframework.transaction.annotation.Transactional
    public CobroDetalle saveCobroDetalle(CobroDetalleInput input) {
        boolean edicion = input.getId() != null && service.findById(input.getId()).isPresent();
        CobroDetalle cd = guardarDetalle(input);
        if (edicion && cd != null) emitirCobroEditado(cd.getCobro());
        return cd;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.franco.dev.eventos.EventoEmisor eventoEmisor;

    @org.springframework.beans.factory.annotation.Autowired
    private com.franco.dev.repository.operaciones.VentaRepository ventaRepository;

    void emitirCobroEditado(com.franco.dev.domain.operaciones.Cobro cobro) {
        if (cobro == null) return;
        com.franco.dev.domain.operaciones.Venta venta = ventaRepository.findFirstByCobro_Id(cobro.getId());
        if (venta == null) return;
        List<CobroDetalle>[] cache = new List[1];
        java.util.function.Supplier<List<CobroDetalle>> detalles = () -> {
            if (cache[0] == null) cache[0] = service.findByCobroId(cobro.getId());
            return cache[0];
        };
        eventoEmisor.emitirProyeccion("venta.cobro_editado", "media",
                venta.getUsuario() != null ? venta.getUsuario().getId() : null,
                "venta/" + venta.getSucursalId() + "/" + venta.getId(),
                () -> com.franco.dev.eventos.EventosVenta.venta(venta, null, detalles.get()),
                () -> {
                    java.util.Map<String, Object> a = com.franco.dev.eventos.EventosVenta.atributosVenta(venta, detalles.get());
                    a.keySet().retainAll(java.util.Arrays.asList("formas_pago", "monedas", "caja_id"));
                    return a;
                });
    }

    /** Guardado sin aviso: lo usan saveVenta, saveVentaDelivery y CobroGraphQL. */
    public CobroDetalle guardarDetalle(CobroDetalleInput input) {
        auditorUsuarioId.verificar("CobroDetalleGraphQL.saveCobroDetalle", input.getUsuarioId());
        ModelMapper m = new ModelMapper();
        CobroDetalle e = m.map(input, CobroDetalle.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getMonedaId() != null) e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if (input.getFormaPagoId() != null)
            e.setFormaPago(formaPagoService.findById(input.getFormaPagoId()).orElse(null));
        if (input.getCobroId() != null) e.setCobro(cobroService.findById(input.getCobroId()).orElse(null));
        CobroDetalle cobroDetalle = service.saveAndSend(e, false);
        return cobroDetalle;
    }

    /** La lista va en una sola TX y avisa una vez por cobro editado (fase 2, #246). */
    @org.springframework.transaction.annotation.Transactional
    public List<CobroDetalle> saveCobroDetalleList(List<CobroDetalleInput> cobroDetalleInputList) {
        List<CobroDetalle> cobroDetalleList = new ArrayList<>();
        java.util.Map<Long, com.franco.dev.domain.operaciones.Cobro> editados = new java.util.LinkedHashMap<>();
        if (cobroDetalleInputList != null) {
            for (CobroDetalleInput input : cobroDetalleInputList) {
                boolean edicion = input.getId() != null && service.findById(input.getId()).isPresent();
                CobroDetalle cobroDetalle = guardarDetalle(input);
                if (edicion && cobroDetalle != null && cobroDetalle.getCobro() != null)
                    editados.putIfAbsent(cobroDetalle.getCobro().getId(), cobroDetalle.getCobro());
                cobroDetalleList.add(cobroDetalle);
            }
        }
        editados.values().forEach(this::emitirCobroEditado);
        return cobroDetalleList;
    }

    public Boolean deleteCobroDetalle(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countCobroDetalle() {
        return service.count();
    }


}
