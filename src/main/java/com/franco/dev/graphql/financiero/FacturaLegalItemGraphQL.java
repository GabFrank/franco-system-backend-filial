package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
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
public class FacturaLegalItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FacturaLegalItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FacturaLegalService facturaLegalService;

    @Autowired
    private VentaItemService ventaItemService;

    @Autowired
    private PresentacionService presentacionService;

    public Optional<FacturaLegalItem> facturaLegalItem(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<FacturaLegalItem> facturaLegalItens(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public FacturaLegalItem saveFacturaLegalItem(FacturaLegalItemInput input) {
        ModelMapper m = new ModelMapper();
        FacturaLegalItem e = m.map(input, FacturaLegalItem.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getFacturaLegalId() != null) e.setFacturaLegal(facturaLegalService.findById(input.getFacturaLegalId()).orElse(null));
        if (input.getVentaItemId() != null) e.setVentaItem(ventaItemService.findById(input.getVentaItemId()).orElse(null));
        if(input.getPresentacionId() != null) e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        return service.saveAndSend(e, false);
    }

    public Boolean deleteFacturaLegalItem(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countFacturaLegalItem() {
        return service.count();
    }


}
