package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.domain.financiero.MonedaBilletes;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.security.Unsecured;
import com.franco.dev.service.financiero.ConteoMonedaService;
import com.franco.dev.service.financiero.ConteoService;
import com.franco.dev.service.financiero.MonedaBilleteService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.seguridad.RolSesionService;
import org.springframework.transaction.annotation.Transactional;
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
public class ConteoMonedaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ConteoMonedaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private MonedaBilleteService monedaBilleteService;

    @Autowired
    private ConteoService conteoService;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private RolSesionService rolSesionService;

    /** Transaccional para leer el conteo (LAZY). */
    @Transactional(readOnly = true)
    public Optional<ConteoMoneda> conteoMoneda(Long id, Long sucId) {
        Optional<ConteoMoneda> cm = service.findById(id);
        cm.ifPresent(c -> pdvCajaService.exigirAccesoAConteo(c.getConteo() != null ? c.getConteo().getId() : null));
        return cm;
    }

    public List<ConteoMoneda> conteoMonedas(int page, int size, Long sucId) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Unsecured
    public ConteoMoneda saveConteoMoneda(ConteoMonedaInput input) {
        ModelMapper m = new ModelMapper();
        ConteoMoneda e = m.map(input, ConteoMoneda.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getConteoId() != null) {
            Conteo conteo = conteoService.findById(input.getConteoId()).orElse(null);
            e.setConteo(conteo);
            if (conteo != null && e.getSucursalId() == null) {
                e.setSucursalId(conteo.getSucursalId());
            }
        }
        if (input.getMonedaBilletesId() != null) {
            MonedaBilletes monedaBilletes = monedaBilleteService.findById(input.getMonedaBilletesId()).orElse(null);
            e.setMonedaBilletes(monedaBilletes);
        }
        return service.saveAndSend(e, false);
    }

    public List<ConteoMoneda> conteoMonedasPorConteoId(Long id, Long sucId) {
        pdvCajaService.exigirAccesoAConteo(id);
        return service.findByConteoId(id);
    }

//    public List<ConteoMoneda> conteoMonedasSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteConteoMoneda(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countConteoMoneda() {
        return service.count();
    }


}
