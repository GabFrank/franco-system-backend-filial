package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.security.Unsecured;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ConteoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(ConteoGraphQL.class);
    @Autowired
    private ConteoService service;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private PaisService paisService;
    @Autowired
    private ConteoMonedaGraphQL conteoMonedaGraphQL;
    @Autowired
    private PdvCajaService pdvCajaService;
    @Autowired
    private MovimientoCajaService movimientoCajaService;
    @Autowired
    private MonedaService monedaService;
    @Autowired
    private CambioService cambioService;
    @Autowired
    private SucursalService sucursalService;

    public Optional<Conteo> conteo(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<Conteo> conteos(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }


    @Unsecured()
    public Conteo saveConteo(ConteoInput input, List<ConteoMonedaInput> conteoMonedaInputList, Long cajaId, Boolean apertura) {
        log.info("[FILIAL saveConteo] INICIO -> cajaId={}, apertura={}, usuarioId={}, totalGs={}, totalRs={}, totalDs={}, cantConteoMoneda={}",
                cajaId, apertura,
                input != null ? input.getUsuarioId() : null,
                input != null ? input.getTotalGs() : null,
                input != null ? input.getTotalRs() : null,
                input != null ? input.getTotalDs() : null,
                conteoMonedaInputList != null ? conteoMonedaInputList.size() : "null");
        ModelMapper m = new ModelMapper();
        Conteo e = m.map(input, Conteo.class);
        Conteo conteo = null;
        PdvCaja pdvCaja = pdvCajaService.findById(cajaId).orElse(null);
        if (pdvCaja == null) {
            log.error("[FILIAL saveConteo] No se encontro la PdvCaja con id={}. No se puede guardar el conteo de apertura. Abortando.", cajaId);
            return null;
        }
        if (pdvCaja != null) {
            if (input.getUsuarioId() != null) {
                e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
            }
            e.setSucursalId(sucursalService.sucursalActual().getId());
            boolean esApertura = Boolean.TRUE.equals(apertura);
            if (!esApertura && pdvCaja.getConteoCierre() != null) {
                log.warn("[FILIAL saveConteo] La caja id={} ya tiene conteo de cierre. Operacion idempotente.", cajaId);
                return pdvCaja.getConteoCierre();
            }
            conteo = service.saveAndSend(e, false);
            log.info("[FILIAL saveConteo] Conteo persistido -> conteoId={}, sucursalId={}",
                    conteo != null ? conteo.getId() : null, e.getSucursalId());
            List<Moneda> monedaList = monedaService.findAll2();
            if (conteo != null) {
                if (esApertura && pdvCaja.getConteoApertura() == null) {
                    log.warn("entranndo enn apertura");
                    pdvCaja.setConteoApertura(conteo);
                    pdvCaja.setFechaApertura(LocalDateTime.now());
                    pdvCajaService.saveAndSend(pdvCaja, false);
                    for (Moneda moneda : monedaList) {
                        MovimientoCaja movimientoCaja = new MovimientoCaja();
                        if (moneda.getDenominacion().contains("GUARANI")) {
                            movimientoCaja.setMoneda(moneda);
                            movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                            movimientoCaja.setCantidad(input.getTotalGs());
                        } else if (moneda.getDenominacion().contains("REAL")) {
                            movimientoCaja.setMoneda(moneda);
                            movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                            movimientoCaja.setCantidad(input.getTotalRs());
                        } else if (moneda.getDenominacion().contains("DOLAR")) {
                            movimientoCaja.setMoneda(moneda);
                            movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                            movimientoCaja.setCantidad(input.getTotalDs());
                        }
                        if (movimientoCaja.getMoneda() != null) {
                            movimientoCaja.setPdvCaja(pdvCaja);
                            movimientoCaja.setReferencia(conteo.getId());
                            movimientoCaja.setTipoMovimiento(PdvCajaTipoMovimiento.CAJA_INICIAL);
                            movimientoCaja.setActivo(true);
                            movimientoCaja.setUsuario(conteo.getUsuario());
                            movimientoCajaService.saveAndSend(movimientoCaja, false);
                        }
                    }
                } else if (!esApertura) {
                    pdvCaja.setConteoCierre(conteo);
                    pdvCaja.setFechaCierre(LocalDateTime.now());
                    pdvCaja.setActivo(false);
                    pdvCajaService.saveAndSend(pdvCaja, false);
                    for (Moneda moneda : monedaList) {
                        MovimientoCaja movimientoCaja = new MovimientoCaja();
                        if (moneda.getDenominacion().contains("GUARANI")) {
                            movimientoCaja.setMoneda(moneda);
                            movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                            movimientoCaja.setCantidad(input.getTotalGs());
                        } else if (moneda.getDenominacion().contains("REAL")) {
                            movimientoCaja.setMoneda(moneda);
                            movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                            movimientoCaja.setCantidad(input.getTotalRs());
                        } else if (moneda.getDenominacion().contains("DOLAR")) {
                            movimientoCaja.setMoneda(moneda);
                            movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                            movimientoCaja.setCantidad(input.getTotalDs());
                        }
                        if (movimientoCaja.getMoneda() != null) {
                            movimientoCaja.setPdvCaja(pdvCaja);
                            movimientoCaja.setReferencia(conteo.getId());
                            movimientoCaja.setTipoMovimiento(PdvCajaTipoMovimiento.CAJA_FINAL);
                            movimientoCaja.setActivo(true);
                            movimientoCaja.setUsuario(conteo.getUsuario());
                            movimientoCajaService.saveAndSend(movimientoCaja, false);
                        }
                    }
                }
                if (conteoMonedaInputList != null && !conteoMonedaInputList.isEmpty()) {
                    for (ConteoMonedaInput conteoMonedaInput : conteoMonedaInputList) {
                        conteoMonedaInput.setConteoId(conteo.getId());
                        conteoMonedaInput.setUsuarioId(input.getUsuarioId());
                        conteoMonedaInput.setSucursalId(conteo.getSucursalId());
                        conteoMonedaGraphQL.saveConteoMoneda(conteoMonedaInput);
                    }
                }
            } else {
                log.error("[FILIAL saveConteo] El conteo no se pudo persistir (saveAndSend retorno null). Eliminando caja id={} para no dejar caja huerfana.", pdvCaja.getId());
                pdvCajaService.deleteById(pdvCaja.getId());
            }
        }
        if (conteo == null) {
            log.error("[FILIAL saveConteo] FIN con conteo null (cajaId={}). Se retorna null.", cajaId);
            return null;
        }
        log.info("[FILIAL saveConteo] FIN OK -> retornando conteoId={} (cajaId={})", conteo.getId(), cajaId);
        return conteo;
    }

    public Boolean deleteConteo(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countConteo() {
        return service.count();
    }


}
