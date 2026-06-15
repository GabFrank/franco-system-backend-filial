package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaBalance;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.graphql.financiero.input.PdvCajaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.ConteoService;
import com.franco.dev.service.financiero.MaletinService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PdvCajaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(PdvCajaGraphQL.class);

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

    public CajaBalance balancePorCajaId(Long id) {
        return service.getBalance(id);
    }

    public PdvCaja savePdvCaja(PdvCajaInput input) {
        log.info("[FILIAL savePdvCaja] INICIO -> id={}, sucursalId={}, maletinId={}, usuarioId={}, estado={}, activo={}, fechaApertura={}",
                input != null ? input.getId() : null,
                input != null ? input.getSucursalId() : null,
                input != null ? input.getMaletinId() : null,
                input != null ? input.getUsuarioId() : null,
                input != null ? input.getEstado() : null,
                input != null ? input.getActivo() : null,
                input != null ? input.getFechaApertura() : null);
        try {
            ModelMapper m = new ModelMapper();
            PdvCaja e = m.map(input, PdvCaja.class);
            if (input.getUsuarioId() != null) {
                e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
                if (e.getUsuario() == null) {
                    log.warn("[FILIAL savePdvCaja] usuarioId={} no existe en la filial", input.getUsuarioId());
                }
            }
            if (input.getVerificadoPorId() != null) {
                e.setVerificadoPor(usuarioService.findById(input.getVerificadoPorId()).orElse(null));
            }
            if (input.getConteoAperturaId() != null)
                e.setConteoApertura(conteoService.findById(input.getConteoAperturaId()).orElse(null));
            if (input.getConteoCierreId() != null)
                e.setConteoCierre(conteoService.findById(input.getConteoCierreId()).orElse(null));
            if (input.getMaletinId() != null) {
                e.setMaletin(maletinService.findById(input.getMaletinId()).orElse(null));
                if (e.getMaletin() == null) {
                    log.warn("[FILIAL savePdvCaja] maletinId={} no existe en la filial", input.getMaletinId());
                }
            }
            PdvCaja pdvCaja = service.saveAndSend(e, false);
            log.info("[FILIAL savePdvCaja] OK -> caja guardada con id={}, sucursalId={}",
                    pdvCaja != null ? pdvCaja.getId() : null,
                    pdvCaja != null ? pdvCaja.getSucursalId() : null);
            return pdvCaja;
        } catch (Exception ex) {
            log.error("[FILIAL savePdvCaja] ERROR al guardar caja (sucursalId={}, maletinId={}): {}",
                    input != null ? input.getSucursalId() : null,
                    input != null ? input.getMaletinId() : null,
                    ex.getMessage(), ex);
            throw ex;
        }
    }

    public PdvCaja transferirCaja(Long cajaId, Long usuarioId) {
        return service.transferirCaja(cajaId, usuarioId);
    }

    // public List<PdvCaja> pdvCajasSearch(String texto){
    // return service.findByAll(texto);
    // }

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

    public Page<PdvCaja> cajasWithFilters(Long cajaId, PdvCajaEstado estado, Long maletinId, Long cajeroId,
            String fechaInicio, String fechaFin, Long sucId, Boolean verificado, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        // store result in variable and then return it
        Page<PdvCaja> pdvCajaPage = service.findAllWithFilters(cajaId, estado, maletinId, cajeroId, fechaInicio,
                fechaFin, sucId, verificado, pageable);
        return pdvCajaPage;
    }
}