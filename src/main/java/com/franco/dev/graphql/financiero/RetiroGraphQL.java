package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.graphql.financiero.input.RetiroDetalleInput;
import com.franco.dev.graphql.financiero.input.RetiroInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.seguridad.RolSesionService;
import com.franco.dev.service.financiero.RetiroDetalleService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.impresion.dto.RetiroDto;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RetiroGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private AuditorUsuarioId auditorUsuarioId;

    @Autowired
    private RetiroService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private RolSesionService rolSesionService;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private RetiroDetalleGraphQL retiroDetalleGraphQL;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private RetiroDetalleService retiroDetalleService;

    @Autowired
    private MonedaService monedaService;

    /** Transaccional para leer la caja (LAZY) del retiro. */
    @Transactional(readOnly = true)
    public Optional<Retiro> retiro(Long id, Long sucId) {
        Optional<Retiro> retiro = service.findById(id);
        retiro.ifPresent(r -> pdvCajaService.exigirAccesoARegistroDeCaja(r.getCajaSalida()));
        return retiro;
    }

    public List<Retiro> retiros(int page, int size, Long sucId) {
        rolSesionService.exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Retiro> retiroListPorCajaSalidaId(Long id, Long sucId) {
        pdvCajaService.exigirAccesoACaja(id);
        return service.findByCajaSalidaId(id);
    }

    @Transactional
    public Retiro saveRetiro(RetiroInput input, List<RetiroDetalleInput> retiroDetalleInputList, String printerName, String local,
            Boolean imprimirEnCliente) throws GraphQLException {
        // Edicion solo si el retiro existe: un id inventado no puede esquivar la firma (issue #142).
        boolean edicion = input.getId() != null && service.findById(input.getId()).isPresent();
        if (!edicion) {
            // Sin caja de salida un retiro nuevo esquivaba el control de efectivo.
            PdvCaja caja = input.getCajaSalidaId() != null
                    ? pdvCajaService.findById(input.getCajaSalidaId()).orElse(null) : null;
            if (caja == null) {
                throw new GraphQLException("Caja no encontrada");
            }
            pdvCajaService.controlarAccesoEnAlta("RetiroGraphQL.saveRetiro", caja);
        }
        if (edicion) {
            auditorUsuarioId.verificar("RetiroGraphQL.saveRetiro", input.getUsuarioId());
        } else {
            input.setUsuarioId(auditorUsuarioId.firmante("RetiroGraphQL.saveRetiro", input.getUsuarioId(),
                    input.getCajaSalidaId()));
        }
        ModelMapper m = new ModelMapper();
        Retiro e = m.map(input, Retiro.class);

        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getCajaSalidaId() != null)
            e.setCajaSalida(pdvCajaService.findById(input.getCajaSalidaId()).orElse(null));
        if (input.getCajaEntradaId() != null)
            e.setCajaEntrada(pdvCajaService.findById(input.getCajaEntradaId()).orElse(null));
        if (input.getResponsableId() != null)
            e.setResponsable(funcionarioService.findById(input.getResponsableId()).orElse(null));
        if (!edicion && e.getCajaSalida() != null) {
            exigirEfectivo(e.getCajaSalida(), retiroDetalleInputList);
        }
        Retiro retiro = service.saveAndSend(e, false);
        if (retiro != null) {
            for (RetiroDetalleInput r : retiroDetalleInputList) {
                r.setRetiroId(retiro.getId());
                r.setUsuarioId(retiro.getUsuario().getId());
                r.setCreadoEn(retiro.getCreadoEn());
                if (r.getCantidad() != null && r.getCantidad() > 0) retiroDetalleGraphQL.guardarDetalle(r);
            }
            RetiroDto retiroDto = new RetiroDto();
            retiroDto.setId(retiro.getId());
            retiroDto.setCajaId(retiro.getCajaSalida() != null ? retiro.getCajaSalida().getId() : null);
            retiroDto.setSucursalId(retiro.getSucursalId());
            retiroDto.setFecha(retiro.getCreadoEn());
            retiroDto.setResponsable(retiro.getResponsable());
            retiroDto.setRetiroGs(input.getRetiroGs());
            retiroDto.setRetiroRs(input.getRetiroRs());
            retiroDto.setRetiroDs(input.getRetiroDs());
            retiroDto.setUsuario(retiro.getUsuario());
            // imprimirEnCliente: el desktop pide el ticket despues con ticketEscpos(RETIRO) y lo imprime el.
            if (!Boolean.TRUE.equals(imprimirEnCliente)) {
                // Despues del commit: el bloqueo de la caja no espera a la impresora y no sale ticket de
                // un retiro que se revierte (issue #80).
                Runnable imprimir = () -> impresionService.printRetiro(retiroDto, printerName, local, false);
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            imprimir.run();
                        }
                    });
                } else {
                    imprimir.run();
                }
            }
        }
        return retiro;

    }

    /**
     * Control de efectivo de un retiro nuevo con la cuenta del cierre, con la caja bloqueada hasta el
     * commit (issue #80). Va antes de guardar nada: despues, el balance ya contaria el propio retiro y
     * rechazaria el retiro "de todo" del cierre.
     */
    private void exigirEfectivo(PdvCaja caja, List<RetiroDetalleInput> detalles) {
        pdvCajaService.bloquearSaldoCaja(caja.getId());
        PdvCajaBalanceDto balance = pdvCajaService.generarBalance(caja);
        Map<Long, Double> porMoneda = new HashMap<>();
        for (RetiroDetalleInput d : detalles) {
            if (d.getMonedaId() != null && d.getCantidad() != null && d.getCantidad() > 0) {
                porMoneda.merge(d.getMonedaId(), d.getCantidad(), Double::sum);
            }
        }
        for (Map.Entry<Long, Double> m : porMoneda.entrySet()) {
            String denominacion = monedaService.findById(m.getKey()).map(Moneda::getDenominacion).orElse(null);
            if (PdvCajaService.superaSaldo(balance, denominacion, m.getValue())) {
                throw new GraphQLException("El valor de retiro es mayor al total en caja: " + denominacion);
            }
        }
    }

//    public List<Retiro> retirosSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteRetiro(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countRetiro() {
        return service.count();
    }

    public Boolean reimprimirRetiro(Long id, String printerName, String local, Long sucId) {
        Retiro retiro = service.findById(id).orElseThrow(() -> new GraphQLException("El retiro " + id + " no existe"));
        // Fuera del try: un rechazo es un error, no un "intente de nuevo".
        pdvCajaService.exigirAccesoARegistroDeCaja(retiro.getCajaSalida());
        try {
            RetiroDto retiroDto = new RetiroDto();
            retiroDto.setId(retiro.getId());
            retiroDto.setCajaId(retiro.getCajaSalida() != null ? retiro.getCajaSalida().getId() : null);
            retiroDto.setSucursalId(retiro.getSucursalId());
            retiroDto.setFecha(retiro.getCreadoEn());
            retiroDto.setResponsable(retiro.getResponsable());
            retiroDto.setRetiroGs(retiroDetalleService.findByRetiroIdAndMonedaId(retiro.getId(), Long.valueOf(1)));
            retiroDto.setRetiroRs(retiroDetalleService.findByRetiroIdAndMonedaId(retiro.getId(), Long.valueOf(2)));
            retiroDto.setRetiroDs(retiroDetalleService.findByRetiroIdAndMonedaId(retiro.getId(), Long.valueOf(3)));
            retiroDto.setUsuario(retiro.getUsuario());
            return impresionService.printRetiro(retiroDto, printerName, local, true, null, true);
        } catch (Exception e) {
            return false;
        }

    }


}
