package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.security.Unsecured;
import graphql.GraphQLException;
import com.franco.dev.service.configuracion.DesktopPrinterConfigService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    @Autowired
    private DesktopPrinterConfigService desktopPrinterConfigService;

    public Optional<Conteo> conteo(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<Conteo> conteos(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }


    @Unsecured()
    @Transactional
    public Conteo saveConteo(ConteoInput input, List<ConteoMonedaInput> conteoMonedaInputList, Long cajaId, Boolean apertura, Boolean imprimirBalance) {
        log.info("[FILIAL saveConteo] INICIO -> cajaId={}, apertura={}, imprimirBalance={}, usuarioId={}, totalGs={}, totalRs={}, totalDs={}, cantConteoMoneda={}",
                cajaId, apertura, imprimirBalance,
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
            boolean cierreRealizado = false;
            if (conteo != null) {
                if (esApertura && pdvCaja.getConteoApertura() == null) {
                    log.warn("entranndo enn apertura");
                    pdvCaja.setConteoApertura(conteo);
                    pdvCaja.setFechaApertura(LocalDateTime.now());
                    pdvCajaService.saveAndSend(pdvCaja, false);
                    crearMovimientosDeConteo(pdvCaja, conteo, monedaList, input.getTotalGs(), input.getTotalRs(),
                            input.getTotalDs(), PdvCajaTipoMovimiento.CAJA_INICIAL);
                } else if (!esApertura) {
                    pdvCaja.setConteoCierre(conteo);
                    pdvCaja.setFechaCierre(LocalDateTime.now());
                    pdvCaja.setActivo(false);
                    pdvCajaService.saveAndSend(pdvCaja, false);
                    cierreRealizado = true;
                    crearMovimientosDeConteo(pdvCaja, conteo, monedaList, input.getTotalGs(), input.getTotalRs(),
                            input.getTotalDs(), PdvCajaTipoMovimiento.CAJA_FINAL);
                }
                if (conteoMonedaInputList != null && !conteoMonedaInputList.isEmpty()) {
                    for (ConteoMonedaInput conteoMonedaInput : conteoMonedaInputList) {
                        conteoMonedaInput.setConteoId(conteo.getId());
                        conteoMonedaInput.setUsuarioId(input.getUsuarioId());
                        conteoMonedaInput.setSucursalId(conteo.getSucursalId());
                        conteoMonedaGraphQL.saveConteoMoneda(conteoMonedaInput);
                    }
                }
                if (cierreRealizado) {
                    programarImpresionBalanceCierre(cajaId, imprimirBalance);
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

    /**
     * Corrige los montos de un conteo de apertura o cierre ya cargado, sin sobreescribirlo.
     * <p>
     * En lugar de modificar el conteo existente se inserta una version nueva enlazada al anterior
     * ({@code conteoAnteriorId}) y se repunta la caja hacia ella, de modo que quede el historial de
     * lo que decia antes, quien lo corrigio y cuando. Los movimientos de caja del conteo reemplazado
     * se desactivan para que el efectivo disponible de la caja no quede duplicado.
     *
     * @param conteoAnteriorId id del conteo que el cliente tenia en pantalla; si no coincide con el
     *                         actual de la caja significa que otro usuario ya lo edito.
     */
    @Unsecured()
    @Transactional
    public Conteo editarConteoCaja(ConteoInput input, List<ConteoMonedaInput> conteoMonedaInputList, Long cajaId,
                                   Long conteoAnteriorId, Boolean apertura, Long usuarioId) {
        log.info("[FILIAL editarConteoCaja] INICIO -> cajaId={}, conteoAnteriorId={}, apertura={}, usuarioId={}, totalGs={}, totalRs={}, totalDs={}, cantConteoMoneda={}",
                cajaId, conteoAnteriorId, apertura, usuarioId,
                input != null ? input.getTotalGs() : null,
                input != null ? input.getTotalRs() : null,
                input != null ? input.getTotalDs() : null,
                conteoMonedaInputList != null ? conteoMonedaInputList.size() : "null");

        if (!usuarioService.tieneRol(usuarioId, "ADMIN")) {
            throw new GraphQLException("Solo un usuario ADMIN puede editar los montos de una caja");
        }
        if (conteoMonedaInputList == null || conteoMonedaInputList.isEmpty()) {
            throw new GraphQLException("No se recibio el detalle de billetes del conteo");
        }
        PdvCaja pdvCaja = pdvCajaService.findById(cajaId).orElse(null);
        if (pdvCaja == null) {
            throw new GraphQLException("No se encontro la caja id=" + cajaId + " en esta sucursal");
        }
        if (Boolean.TRUE.equals(pdvCaja.getVerificado())) {
            throw new GraphQLException("La caja ya fue verificada, no se pueden editar sus montos");
        }
        boolean esApertura = Boolean.TRUE.equals(apertura);
        Conteo conteoAnterior = esApertura ? pdvCaja.getConteoApertura() : pdvCaja.getConteoCierre();
        if (conteoAnterior == null) {
            throw new GraphQLException("La caja no tiene conteo de " + (esApertura ? "apertura" : "cierre") + " para editar");
        }
        if (!conteoAnterior.getId().equals(conteoAnteriorId)) {
            throw new GraphQLException("El conteo fue editado por otro usuario. Vuelva a abrir la caja para ver los montos actuales.");
        }

        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
        if (usuario == null) {
            throw new GraphQLException("El usuario que edita no existe en esta sucursal (filial)");
        }

        Conteo nuevo = new Conteo();
        nuevo.setObservacion(conteoAnterior.getObservacion());
        nuevo.setUsuario(usuario);
        nuevo.setSucursalId(sucursalService.sucursalActual().getId());
        nuevo.setConteoAnteriorId(conteoAnterior.getId());
        nuevo = service.saveAndSend(nuevo, false);
        if (nuevo == null) {
            throw new GraphQLException("No se pudo guardar la nueva version del conteo");
        }
        log.info("[FILIAL editarConteoCaja] Nueva version de conteo persistida -> conteoId={}, reemplaza a conteoId={}",
                nuevo.getId(), conteoAnterior.getId());

        // El detalle del conteo anterior no se toca: es el historial de lo que se habia cargado.
        for (ConteoMonedaInput conteoMonedaInput : conteoMonedaInputList) {
            conteoMonedaInput.setId(null);
            conteoMonedaInput.setConteoId(nuevo.getId());
            conteoMonedaInput.setUsuarioId(usuarioId);
            conteoMonedaInput.setSucursalId(nuevo.getSucursalId());
            conteoMonedaGraphQL.saveConteoMoneda(conteoMonedaInput);
        }

        // Solo se repunta el conteo: estado, activo, fechas y maletin quedan como estaban.
        if (esApertura) {
            pdvCaja.setConteoApertura(nuevo);
        } else {
            pdvCaja.setConteoCierre(nuevo);
        }
        pdvCajaService.saveAndSend(pdvCaja, false);

        PdvCajaTipoMovimiento tipoMovimiento = esApertura ? PdvCajaTipoMovimiento.CAJA_INICIAL : PdvCajaTipoMovimiento.CAJA_FINAL;
        List<MovimientoCaja> movimientosAnteriores = movimientoCajaService
                .findByTipoMovimientoAndReferencia(tipoMovimiento, conteoAnterior.getId());
        if (movimientosAnteriores != null) {
            for (MovimientoCaja movimientoAnterior : movimientosAnteriores) {
                movimientoAnterior.setActivo(false);
                movimientoCajaService.saveAndSend(movimientoAnterior, false);
            }
            log.info("[FILIAL editarConteoCaja] Movimientos desactivados del conteo reemplazado -> cantidad={}, referencia={}",
                    movimientosAnteriores.size(), conteoAnterior.getId());
        }
        crearMovimientosDeConteo(pdvCaja, nuevo, monedaService.findAll2(), input.getTotalGs(), input.getTotalRs(),
                input.getTotalDs(), tipoMovimiento);

        log.info("[FILIAL editarConteoCaja] FIN OK -> conteoId={} (cajaId={})", nuevo.getId(), cajaId);
        return nuevo;
    }

    /**
     * Registra en movimiento_caja los totales por moneda de un conteo de apertura o cierre.
     */
    private void crearMovimientosDeConteo(PdvCaja pdvCaja, Conteo conteo, List<Moneda> monedaList,
                                          Double totalGs, Double totalRs, Double totalDs,
                                          PdvCajaTipoMovimiento tipoMovimiento) {
        for (Moneda moneda : monedaList) {
            MovimientoCaja movimientoCaja = new MovimientoCaja();
            if (moneda.getDenominacion().contains("GUARANI")) {
                movimientoCaja.setMoneda(moneda);
                movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                movimientoCaja.setCantidad(totalGs);
            } else if (moneda.getDenominacion().contains("REAL")) {
                movimientoCaja.setMoneda(moneda);
                movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                movimientoCaja.setCantidad(totalRs);
            } else if (moneda.getDenominacion().contains("DOLAR")) {
                movimientoCaja.setMoneda(moneda);
                movimientoCaja.setCambio(cambioService.findLastByMonedaId(moneda.getId()));
                movimientoCaja.setCantidad(totalDs);
            }
            if (movimientoCaja.getMoneda() != null) {
                movimientoCaja.setPdvCaja(pdvCaja);
                movimientoCaja.setReferencia(conteo.getId());
                movimientoCaja.setTipoMovimiento(tipoMovimiento);
                movimientoCaja.setActivo(true);
                movimientoCaja.setUsuario(conteo.getUsuario());
                movimientoCajaService.saveAndSend(movimientoCaja, false);
            }
        }
    }

    public Boolean deleteConteo(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countConteo() {
        return service.count();
    }

    private void programarImpresionBalanceCierre(Long cajaId, Boolean imprimirBalance) {
        if (!Boolean.TRUE.equals(imprimirBalance)) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    imprimirBalanceCierreSiCorresponde(cajaId, true);
                }
            });
            log.info("[FILIAL saveConteo] Impresion de balance de cierre programada post-commit. cajaId={}", cajaId);
        } else {
            imprimirBalanceCierreSiCorresponde(cajaId, imprimirBalance);
        }
    }

    private void imprimirBalanceCierreSiCorresponde(Long cajaId, Boolean imprimirBalance) {
        if (!Boolean.TRUE.equals(imprimirBalance)) {
            return;
        }
        try {
            String printerName = desktopPrinterConfigService.getTicketPrinterName().orElse(null);
            String local = desktopPrinterConfigService.getLocalName().orElse(null);
            log.info("[FILIAL saveConteo] Imprimiendo balance de cierre. cajaId={}, printer={}, local={}",
                    cajaId, printerName, local);
            pdvCajaService.imprimirBalance(cajaId, printerName, local);
        } catch (Exception e) {
            log.error("[FILIAL saveConteo] Error al imprimir balance de cierre para cajaId={}. El cierre fue exitoso.",
                    cajaId, e);
        }
    }

}
