package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.graphql.financiero.input.VentaCreditoCuotaInput;
import com.franco.dev.service.financiero.VentaCreditoCuotaService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class VentaCreditoCuotasGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VentaCreditoCuotaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private VentaCreditoService ventaCreditoService;

    @Autowired
    private CobroService cobroService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<VentaCreditoCuota> ventaCreditoCuota(Long id) {
        return service.findById(id);
    }

    public List<VentaCreditoCuota> ventaCreditoCuotas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countVentaCreditoCuota() {
        return service.count();
    }

    public VentaCreditoCuota saveVentaCreditoCuota(VentaCreditoCuotaInput input) {
        ModelMapper m = new ModelMapper();
        VentaCreditoCuota e = m.map(input, VentaCreditoCuota.class);
        if(input.getVencimiento()!=null) e.setVencimiento(toDate(input.getVencimiento()));
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getVentaCreditoId() != null)
            e.setVentaCredito(ventaCreditoService.findById(input.getVentaCreditoId()).orElse(null));
        if (input.getCobroId() != null) e.setCobro(cobroService.findById(input.getCobroId()).orElse(null));
        e = service.saveAndSend(e, false);
//        propagacionService.propagarEntidad(e, TipoEntidad.BANCO);
        return e;
    }


}
