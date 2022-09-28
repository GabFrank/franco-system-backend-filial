package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaBalance;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.graphql.financiero.input.PdvCajaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.ConteoService;
import com.franco.dev.service.financiero.MaletinService;
import com.franco.dev.service.financiero.PdvCajaService;
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
public class PdvCajaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PdvCajaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ConteoService conteoService;

    @Autowired
    private MaletinService maletinService;

    @Autowired
    private ConteoGraphQL conteoGraphQL;

    @Autowired
    private ConteoMonedaGraphQL conteoMonedaGraphQL;

    @Autowired
    private SucursalService sucursalService;

    public Optional<PdvCaja> pdvCaja(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<PdvCaja> pdvCajas(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<PdvCaja> cajasPorFecha(String inicio, String fin, Long sucId) {
        return service.findByDate(inicio, fin);
    }

    public CajaBalance balancePorFecha(String inicio, String fin, Long sucId) {
        List<PdvCaja> pdvCajaList = service.findByDate(inicio, fin);
        Double totalVentaGs = 0.0;
        Double totalVentaRs = 0.0;
        Double totalVentaDs = 0.0;
        Double totalVentaTarjeta = 0.0;
        Double totalVentaCredito = 0.0;
        for (PdvCaja c : pdvCajaList) {
            CajaBalance cb = service.getBalance(c.getId());
            totalVentaGs = totalVentaGs + cb.getTotalVentaGs();
            totalVentaRs = totalVentaRs + cb.getTotalVentaRs();
            totalVentaDs = totalVentaDs + cb.getTotalVentaDs();
            totalVentaTarjeta = totalVentaTarjeta + cb.getTotalTarjeta();
            totalVentaTarjeta = totalVentaCredito + cb.getTotalCredito();
        }
        CajaBalance cajaBalance = new CajaBalance();
        cajaBalance.setTotalVentaGs(totalVentaGs);
        cajaBalance.setTotalVentaRs(totalVentaRs);
        cajaBalance.setTotalVentaDs(totalVentaDs);
        cajaBalance.setTotalTarjeta(totalVentaTarjeta);
        cajaBalance.setTotalCredito(totalVentaCredito);
        return cajaBalance;
    }

    public PdvCaja savePdvCaja(PdvCajaInput input) {
        ModelMapper m = new ModelMapper();
        PdvCaja e = m.map(input, PdvCaja.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getVerificadoPorId() != null) {
            e.setVerificadoPor(usuarioService.findById(input.getVerificadoPorId()).orElse(null));
        }
        if (input.getConteoAperturaId() != null)
            e.setConteoApertura(conteoService.findById(input.getConteoAperturaId()).orElse(null));
        if (input.getConteoCierreId() != null)
            e.setConteoCierre(conteoService.findById(input.getConteoCierreId()).orElse(null));
        if (input.getMaletinId() != null) e.setMaletin(maletinService.findById(input.getMaletinId()).orElse(null));
        PdvCaja pdvCaja = service.saveAndSend(e, false);
        return pdvCaja;
    }

    //    public List<PdvCaja> pdvCajasSearch(String texto){
    //        return service.findByAll(texto);
    //    }

    public Boolean deletePdvCaja(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countPdvCaja() {
        return service.count();
    }

    public PdvCaja cajaAbiertoPorUsuarioId(Long id, Long sucId) {
        return service.findByUsuarioIdAndAbierto(id);
    }

    public PdvCaja imprimirBalance(Long id, String printerName, String local, Long sucId) {
        return service.imprimirBalance(id, printerName, local);
    }

}
