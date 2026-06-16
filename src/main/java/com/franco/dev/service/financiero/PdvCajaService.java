package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.UsuarioService;

import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import static com.franco.dev.utilitarios.DateUtils.toDate;

@Service
@AllArgsConstructor
public class PdvCajaService extends CrudService<PdvCaja, PdvCajaRepository> {

    private final PdvCajaRepository repository;

    @Autowired
    private MaletinService maletinService;

    @Autowired
    private ConteoMonedaService conteoMonedaService;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private CobroDetalleService cobroDetalleService;

    @Autowired
    private RetiroDetalleService retiroDetalleService;

    @Autowired
    private GastoService gastoService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private CobroService cobroService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private DeliveryService deliveryService;

    @Override
    public PdvCajaRepository getRepository() {
        return repository;
    }

    public Optional<PdvCaja> findById(Long id) {
        return repository.findById(id);
    }

    public List<PdvCaja> findByDate(String inicio, String fin) {
        return repository.findByCreadoEnBetween(toDate(inicio), toDate(fin));
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PdvCaja save(PdvCaja entity) throws GraphQLException {
        Maletin m = null;
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setId(getNextId());
        }
        if (entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getMaletin() != null) {
            m = maletinService.findById(entity.getMaletin().getId()).orElse(null);
            if (Boolean.TRUE.equals(entity.getActivo())) {
                if (m != null) {
                    m.setAbierto(true);
                }
            } else if (m != null && !repository.existsByMaletinIdAndActivoTrueAndIdNot(m.getId(), entity.getId())) {
                m.setAbierto(false);
            }
        }

        if (entity.getUsuario() == null || entity.getUsuario().getId() == null) {
            throw new GraphQLException("No se puede abrir la caja: el usuario no existe en esta sucursal (filial). Verifique que el usuario este replicado en la filial.");
        }

        if (Boolean.TRUE.equals(entity.getActivo())) {
            List<PdvCaja> aux = repository.findByUsuarioIdAndActivo(entity.getUsuario().getId(), true);
            if (aux.size() > 0 && !aux.get(0).getId().equals(entity.getId()))
                throw new GraphQLException("Ya existe una caja abierta");
        }

        PdvCaja e = super.save(entity);
        maletinService.save(m);
        return e;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PdvCaja saveAndSend(PdvCaja entity, Boolean recibir) throws GraphQLException {
        Maletin m = null;
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setId(getNextId());
        }
        if (entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getMaletin() != null) {
            m = maletinService.findById(entity.getMaletin().getId()).orElse(null);
            if (Boolean.TRUE.equals(entity.getActivo())) {
                if (m != null) {
                    m.setAbierto(true);
                }
            } else if (m != null && !repository.existsByMaletinIdAndActivoTrueAndIdNot(m.getId(), entity.getId())) {
                m.setAbierto(false);
            }
        }
        if (entity.getUsuario() == null || entity.getUsuario().getId() == null) {
            throw new GraphQLException("No se puede abrir la caja: el usuario no existe en esta sucursal (filial). Verifique que el usuario este replicado en la filial.");
        }
        if (Boolean.TRUE.equals(entity.getActivo())) {
            List<PdvCaja> aux = repository.findByUsuarioIdAndActivo(entity.getUsuario().getId(), true);
            if (aux.size() > 0 && !aux.get(0).getId().equals(entity.getId()))
                throw new GraphQLException("Ya existe una caja abierta");
        }
        if (entity.getSucursalId() == null)
            entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        PdvCaja e = super.save(entity);
        maletinService.save(m);
        return e;
    }

    /**
     * Gets the next sequential ID for PdvCaja
     *
     * @return The next ID in sequence
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    private Long getNextId() {
        Long maxId = repository.findMaxId();
        return maxId != null ? maxId + 1 : 1L;
    }

    public PdvCaja findByUsuarioIdAndAbierto(Long id) {
        List<PdvCaja> pdvCajaList = repository.findByUsuarioIdAndActivo(id, true);
        if (pdvCajaList.size() > 0) {
            return pdvCajaList.get(0);
        } else {
            return null;
        }
    }

    @Override
    public Boolean deleteById(Long id) {
        PdvCaja pdvCaja = findById(id).orElse(null);
        maletinService.cerrarMaletin(pdvCaja.getMaletin().getId());
        return super.deleteById(pdvCaja.getId());
    }

    // public PdvCajaBalanceDto generarBalance(PdvCaja pdvCaja){
    // PdvCajaBalanceDto balance = new PdvCajaBalanceDto();
    // if(pdvCaja!=null){
    // balance.setIdCaja(pdvCaja.getId());
    // List<ConteoMoneda> conteoMonedaAperList =
    // conteoMonedaService.findByConteoId(pdvCaja.getConteoApertura().getId());
    // List<ConteoMoneda> conteoMonedaCierreList =
    // conteoMonedaService.findByConteoId(pdvCaja.getConteoCierre().getId());
    // List<CobroDetalle> cobroDetalleList =
    // cobroDetalleService.findByCajaId(pdvCaja.getId());
    // List<RetiroDetalle> retiroDetalleList =
    // retiroDetalleService.findByCajId(pdvCaja.getId());
    // List<Gasto> gastoList = gastoService.findByCajaId(pdvCaja.getId());
    // if(!conteoMonedaAperList.isEmpty() && !conteoMonedaCierreList.isEmpty()){
    // Double totalGsAper = 0.0;
    // Double totalRsAper = 0.0;
    // Double totalDsAper = 0.0;
    // Double totalGsCierre = 0.0;
    // Double totalRsCierre = 0.0;
    // Double totalDsCierre = 0.0;
    // for(ConteoMoneda c: conteoMonedaAperList){
    // if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("GUARANI")){
    // totalGsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
    // } else
    // if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("REAL")){
    // totalRsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
    // } else
    // if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("DOLAR")){
    // totalDsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
    // }
    // }
    // for(ConteoMoneda c: conteoMonedaCierreList){
    // if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("GUARANI")){
    // totalGsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
    // } else
    // if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("REAL")){
    // totalRsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
    // } else
    // if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("DOLAR")){
    // totalDsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
    // }
    // }
    // balance.setTotalGsAper(totalGsAper);
    // balance.setTotalRsAper(totalRsAper);
    // balance.setTotalDsAper(totalDsAper);
    // balance.setTotalGsCierre(totalGsCierre);
    // balance.setTotalRsCierre(totalRsCierre);
    // balance.setTotalDsCierre(totalDsCierre);
    //
    // }
    //// List<MovimientoCaja> movimientoCajaList =
    // movimientoCajaService.findByPdvCajaId(pdvCaja.getId());
    // Double totalVentaGs = 0.0;
    // Double totalVentaRs = 0.0;
    // Double totalVentaDs = 0.0;
    // Double totalRetiroGs = 0.0;
    // Double totalRetiroRs = 0.0;
    // Double totalRetiroDs = 0.0;
    // Double totalTarjeta = 0.0;
    // Double totalGastoGs = 0.0;
    // Double totalGastoRs = 0.0;
    // Double totalGastoDs = 0.0;
    // for(RetiroDetalle retiroDetalle: retiroDetalleList){
    // if(retiroDetalle.getMoneda().getDenominacion().contains("GUARANI")){
    // totalRetiroGs += retiroDetalle.getCantidad();
    // }
    // else if(retiroDetalle.getMoneda().getDenominacion().contains("REAL")){
    // totalRetiroRs += retiroDetalle.getCantidad();
    //
    // }
    // else if(retiroDetalle.getMoneda().getDenominacion().contains("DOLAR")){
    // totalRetiroDs += retiroDetalle.getCantidad();
    // }
    // }
    // for(Gasto gasto: gastoList){
    // totalGastoGs += gasto.getRetiroGs();
    // totalGastoRs += gasto.getRetiroRs();
    // totalGastoDs += gasto.getRetiroDs();
    // }
    // for(CobroDetalle cobroDetalle: cobroDetalleList){
    // if(cobroDetalle.getMoneda().getDenominacion().contains("GUARANI")){
    // if(cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")){
    // totalVentaGs += cobroDetalle.getValor();
    // } else if(cobroDetalle.getFormaPago().getDescripcion().contains("TARJETA")){
    // totalTarjeta += cobroDetalle.getValor();
    // }
    // }
    // else if(cobroDetalle.getMoneda().getDenominacion().contains("REAL")){
    // if(cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")){
    // totalVentaRs += cobroDetalle.getValor();
    // }
    // }
    // else if(cobroDetalle.getMoneda().getDenominacion().contains("DOLAR")){
    // if(cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")){
    // totalVentaDs += cobroDetalle.getValor();
    // }
    // }
    // }
    // balance.setTotalRetiroGs(totalRetiroGs);
    // balance.setTotalRetiroRs(totalRetiroRs);
    // balance.setTotalRetiroDs(totalRetiroDs);
    // balance.setTotalGastoGs(totalGastoGs);
    // balance.setTotalGastoRs(totalGastoRs);
    // balance.setTotalGastoDs(totalGastoDs);
    // balance.setTotalTarjeta(totalTarjeta);
    // balance.setTotalVentaGs(totalVentaGs);
    // balance.setTotalVentaRs(totalVentaRs);
    // balance.setTotalVentaDs(totalVentaDs);
    // balance.setUsuario(pdvCaja.getUsuario());
    // balance.setFechaApertura(pdvCaja.getFechaApertura());
    // balance.setFechaCierre(pdvCaja.getFechaCierre());
    // balance.setDiferenciaGs(balance.getTotalGsCierre() - balance.getTotalGsAper()
    // + balance.getTotalRetiroGs() + balance.getTotalGastoGs() -
    // balance.getTotalVentaGs());
    // balance.setDiferenciaRs(balance.getTotalRsCierre() - balance.getTotalRsAper()
    // + balance.getTotalRetiroRs() + balance.getTotalGastoRs() -
    // balance.getTotalVentaRs());
    // balance.setDiferenciaDs(balance.getTotalDsCierre() - balance.getTotalDsAper()
    // + balance.getTotalRetiroDs() + balance.getTotalGastoDs() -
    // balance.getTotalVentaDs());
    // }
    // return balance;
    // }

    public PdvCajaBalanceDto generarBalance(PdvCaja pdvCaja) {
        PdvCajaBalanceDto balance = new PdvCajaBalanceDto();
        if (pdvCaja != null && pdvCaja.getConteoApertura() != null) {
            balance.setIdCaja(pdvCaja.getId());
            List<ConteoMoneda> conteoMonedaAperList = conteoMonedaService
                    .findByConteoId(pdvCaja.getConteoApertura().getId());
            List<ConteoMoneda> conteoMonedaCierreList = pdvCaja.getConteoCierre() != null
                    ? conteoMonedaService.findByConteoId(pdvCaja.getConteoCierre().getId())
                    : new ArrayList<>();
            List<RetiroDetalle> retiroDetalleList = retiroDetalleService.findByCajId(pdvCaja.getId());
            List<Gasto> gastoList = gastoService.findByCajaId(pdvCaja.getId());
            List<Venta> ventaList = ventaService.findAllByCajaId(pdvCaja.getId());
            List<Delivery> deliveryList = deliveryService.getRepository().findDeliveryByCajaEstadoAndSucId(
                    pdvCaja.getId(),
                    Arrays.asList(DeliveryEstado.CONCLUIDO, DeliveryEstado.PARA_ENTREGA, DeliveryEstado.ENTREGADO),
                    pdvCaja.getSucursalId());
            if (pdvCaja.getConteoApertura() != null) {
                establecerTotalesApertura(pdvCaja, balance, conteoMonedaAperList);
            }

            if (pdvCaja.getConteoCierre() != null) {
                establecerTotalesCierre(pdvCaja, balance, conteoMonedaCierreList);
            }
            // List<MovimientoCaja> movimientoCajaList =
            // movimientoCajaService.findByPdvCajaId(pdvCaja.getId());
            Double totalGeneral = 0.0;
            Double totalVentaGs = 0.0;
            Double totalVentaRs = 0.0;
            Double totalVentaDs = 0.0;
            Double totalRetiroGs = 0.0;
            Double totalRetiroRs = 0.0;
            Double totalRetiroDs = 0.0;
            Double totalTarjeta = 0.0;
            Double totalTarjetaRs = 0.0;
            Double totalTarjetaDs = 0.0;
            Double totalTransferencia = 0.0;
            Double totalTransferenciaRs = 0.0;
            Double totalTransferenciaDs = 0.0;
            Double totalConvenio = 0.0;
            Double totalGastoGs = 0.0;
            Double totalGastoRs = 0.0;
            Double totalGastoDs = 0.0;
            Double totalDescuento = 0.0;
            Double totalAumento = 0.0;
            Double totalCanceladasGs = 0.0;
            Double totalCanceladasRs = 0.0;
            Double totalCanceladasDs = 0.0;
            Double vueltoGs = 0.0;
            Double vueltoRs = 0.0;
            Double vueltoDs = 0.0;
            Double totalDelivery = 0.0;

            for (Delivery delivery : deliveryList) {
                if (delivery.getEstado().equals(DeliveryEstado.CONCLUIDO)) {
                    totalDelivery = +delivery.getPrecio().getValor();
                }
            }

            for (RetiroDetalle retiroDetalle : retiroDetalleList) {
                if (retiroDetalle.getMoneda().getDenominacion().contains("GUARANI")) {
                    totalRetiroGs += retiroDetalle.getCantidad();
                } else if (retiroDetalle.getMoneda().getDenominacion().contains("REAL")) {
                    totalRetiroRs += retiroDetalle.getCantidad();

                } else if (retiroDetalle.getMoneda().getDenominacion().contains("DOLAR")) {
                    totalRetiroDs += retiroDetalle.getCantidad();
                }
            }
            for (Gasto gasto : gastoList) {
                totalGastoGs += (gasto.getRetiroGs() - gasto.getVueltoGs());
                totalGastoRs += (gasto.getRetiroRs() - gasto.getVueltoRs());
                totalGastoDs += (gasto.getRetiroDs() - gasto.getVueltoDs());
            }

            for (Venta venta : ventaList) {
                totalGeneral += venta.getTotalGs();

                Cobro cobro = cobroService.findById(venta.getCobro().getId()).orElse(null);
                if (cobro != null) {
                    List<CobroDetalle> cobroDetalleList = cobroDetalleService.findByCobroId(cobro.getId());
                    if (venta.getEstado() == VentaEstado.CONCLUIDA
                            || venta.getEstado() == VentaEstado.EN_VERIFICACION) {
                        for (CobroDetalle cobroDetalle : cobroDetalleList) {
                            if (cobroDetalle.getMoneda().getDenominacion().contains("GUARANI")) {
                                if (cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")) {
                                    if (cobroDetalle.getDescuento() != null && cobroDetalle.getDescuento()) {
                                        totalDescuento += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getAumento() != null && cobroDetalle.getAumento()) {
                                        totalAumento += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getVuelto() != null && cobroDetalle.getVuelto()) {
                                        vueltoGs += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getPago() != null && cobroDetalle.getPago()) {
                                        totalVentaGs += cobroDetalle.getValor();
                                    }
                                } else if (cobroDetalle.getFormaPago().getDescripcion().contains("TARJETA")) {
                                    if (cobroDetalle.getAumento() != null && !cobroDetalle.getAumento())
                                        totalTarjeta += cobroDetalle.getValor();
                                } else if (cobroDetalle.getFormaPago().getDescripcion().contains("TRANSFERENCIA")) {
                                    if (cobroDetalle.getAumento() != null && !cobroDetalle.getAumento())
                                        totalTransferencia += cobroDetalle.getValor();
                                } else if (cobroDetalle.getFormaPago().getDescripcion().contains("CONVENIO")) {
                                    totalConvenio += cobroDetalle.getValor();
                                }
                            } else if (cobroDetalle.getMoneda().getDenominacion().contains("REAL")) {
                                if (cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")) {
                                    if (cobroDetalle.getAumento() != null && cobroDetalle.getAumento()) {
                                        totalAumento += cobroDetalle.getValor() * cobroDetalle.getCambio();
                                    } else if (cobroDetalle.getVuelto() != null && cobroDetalle.getVuelto()) {
                                        vueltoRs += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getPago() != null && cobroDetalle.getPago()) {
                                        totalVentaRs += cobroDetalle.getValor();
                                    }
                                } else if (cobroDetalle.getFormaPago().getDescripcion().contains("TARJETA")) {
                                    if (cobroDetalle.getAumento() != null && !cobroDetalle.getAumento())
                                        totalTarjetaRs += cobroDetalle.getValor();
                                } else if (cobroDetalle.getFormaPago().getDescripcion().contains("TRANSFERENCIA")) {
                                    if (cobroDetalle.getAumento() != null && !cobroDetalle.getAumento())
                                        totalTransferenciaRs += cobroDetalle.getValor();
                                }
                            } else if (cobroDetalle.getMoneda().getDenominacion().contains("DOLAR")) {
                                if (cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")) {
                                    if (cobroDetalle.getAumento() != null && cobroDetalle.getAumento()) {
                                        totalAumento += cobroDetalle.getValor() * cobroDetalle.getCambio();
                                    } else if (cobroDetalle.getVuelto() != null && cobroDetalle.getVuelto()) {
                                        vueltoDs += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getPago() != null && cobroDetalle.getPago()) {
                                        totalVentaDs += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getPago() != null && cobroDetalle.getPago()) {
                                        totalVentaDs += cobroDetalle.getValor();
                                    }
                                } else if (cobroDetalle.getFormaPago().getDescripcion().contains("TARJETA")) {
                                    if (cobroDetalle.getAumento() != null && !cobroDetalle.getAumento())
                                        totalTarjetaDs += cobroDetalle.getValor();
                                } else if (cobroDetalle.getFormaPago().getDescripcion().contains("TRANSFERENCIA")) {
                                    if (cobroDetalle.getAumento() != null && !cobroDetalle.getAumento())
                                        totalTransferenciaDs += cobroDetalle.getValor();
                                }
                            }
                        }
                    } else if (venta.getEstado() == VentaEstado.CANCELADA) {
                        for (CobroDetalle cobroDetalle : cobroDetalleList) {
                            if (cobroDetalle.getMoneda().getDenominacion().contains("GUARANI")) {
                                if (cobroDetalle.getPago()) {
                                    totalCanceladasGs += cobroDetalle.getValor();
                                } else if (cobroDetalle.getVuelto()) {
                                    vueltoGs -= cobroDetalle.getValor();
                                } else if (cobroDetalle.getDescuento()) {
                                    totalDescuento -= cobroDetalle.getValor();
                                } else if (cobroDetalle.getAumento()) {
                                    totalAumento -= cobroDetalle.getValor();
                                }
                            } else if (cobroDetalle.getMoneda().getDenominacion().contains("REAL")) {
                                if (cobroDetalle.getPago()) {
                                    totalCanceladasRs += cobroDetalle.getValor();
                                } else if (cobroDetalle.getVuelto()) {
                                    vueltoRs -= cobroDetalle.getValor();
                                }
                            } else if (cobroDetalle.getMoneda().getDenominacion().contains("DOLAR")) {
                                if (cobroDetalle.getPago()) {
                                    totalCanceladasDs += cobroDetalle.getValor();
                                } else if (cobroDetalle.getVuelto()) {
                                    vueltoDs -= cobroDetalle.getValor();
                                }
                            }
                        }
                    }
                }
            }
            balance.setTotalGeneral(totalGeneral - totalDescuento);
            balance.setVueltoGs(vueltoGs);
            balance.setVueltoRs(vueltoRs);
            balance.setVueltoDs(vueltoDs);
            balance.setTotalCanceladasGs(totalCanceladasGs);
            balance.setTotalCanceladasRs(totalCanceladasRs);
            balance.setTotalCanceladasDs(totalCanceladasDs);
            balance.setTotalDescuento(totalDescuento);
            balance.setTotalAumento(totalAumento);
            balance.setTotalRetiroGs(totalRetiroGs);
            balance.setTotalRetiroRs(totalRetiroRs);
            balance.setTotalRetiroDs(totalRetiroDs);
            balance.setTotalGastoGs(totalGastoGs);
            balance.setTotalGastoRs(totalGastoRs);
            balance.setTotalGastoDs(totalGastoDs);
            balance.setTotalTarjeta(totalTarjeta);
            balance.setTotalTarjetaRs(totalTarjetaRs);
            balance.setTotalTarjetaDs(totalTarjetaDs);
            balance.setTotalTransferencia(totalTransferencia);
            balance.setTotalTransferenciaRs(totalTransferenciaRs);
            balance.setTotalTransferenciaDs(totalTransferenciaDs);
            balance.setTotalCredito(totalConvenio);
            balance.setTotalVentaGs(totalVentaGs);
            balance.setTotalVentaRs(totalVentaRs);
            balance.setTotalVentaDs(totalVentaDs);
            balance.setUsuario(pdvCaja.getUsuario());
            balance.setFechaApertura(pdvCaja.getFechaApertura());
            balance.setFechaCierre(pdvCaja.getFechaCierre());
            balance.setDiferenciaGs(balance.getTotalGsCierre() - balance.getTotalGsAper() - totalVentaGs - vueltoGs
                    + totalRetiroGs + totalGastoGs);
            balance.setDiferenciaRs(balance.getTotalRsCierre() - balance.getTotalRsAper() - totalVentaRs - vueltoRs
                    + totalRetiroRs + totalGastoRs);
            balance.setDiferenciaDs(balance.getTotalDsCierre() - balance.getTotalDsAper() - totalVentaDs - vueltoDs
                    + totalRetiroDs + totalGastoDs);
            balance.setSucursal((Sucursal) sucursalService.findById(pdvCaja.getSucursalId()).orElse(null));
        }
        return balance;
    }

    public PdvCaja imprimirBalance(Long id, String printerName, String local) {
        PdvCaja pdvCaja = findById(id).orElse(null);
        if (pdvCaja != null) {
            PdvCajaBalanceDto balanceDto = generarBalance(pdvCaja);
            impresionService.printBalance(balanceDto, printerName, local);
        }
        return pdvCaja;
    }

    public Page<PdvCaja> findAllWithFilters(Long cajaId, PdvCajaEstado estado, Long maletinId, Long cajeroId,
            String fechaInicio, String fechaFin, Long sucId, Boolean verificado, Pageable pageable) {
        return repository.findAllWithFilters(cajaId, estado, maletinId, cajeroId,
                fechaInicio != null ? stringToDate(fechaInicio) : null,
                fechaFin != null ? stringToDate(fechaFin) : null, sucId, verificado, pageable);
    }

    public CajaBalance getBalance(Long id) {
        PdvCaja pdvCaja = findById(id).orElse(null);
        CajaBalance balance = new CajaBalance();
        if (pdvCaja != null) {
            PdvCajaBalanceDto balanceDto = generarBalance(pdvCaja);
            balance.setCajaId(id);
            balance.setTotalGeneral(balanceDto.getTotalGeneral());
            balance.setVueltoGs(balanceDto.getVueltoGs());
            balance.setVueltoRs(balanceDto.getVueltoRs());
            balance.setVueltoDs(balanceDto.getVueltoDs());
            balance.setTotalVentaGs(balanceDto.getTotalVentaGs());
            balance.setTotalVentaRs(balanceDto.getTotalVentaRs());
            balance.setTotalVentaDs(balanceDto.getTotalVentaDs());
            balance.setTotalTarjeta(balanceDto.getTotalTarjeta());
            balance.setTotalTarjetaRs(balanceDto.getTotalTarjetaRs());
            balance.setTotalTarjetaDs(balanceDto.getTotalTarjetaDs());
            balance.setTotalTransferencia(balanceDto.getTotalTransferencia());
            balance.setTotalTransferenciaRs(balanceDto.getTotalTransferenciaRs());
            balance.setTotalTransferenciaDs(balanceDto.getTotalTransferenciaDs());
            balance.setTotalCredito(balanceDto.getTotalCredito());
            balance.setTotalRetiroGs(balanceDto.getTotalRetiroGs());
            balance.setTotalRetiroRs(balanceDto.getTotalRetiroRs());
            balance.setTotalRetiroDs(balanceDto.getTotalRetiroDs());
            balance.setTotalGastoGs(balanceDto.getTotalGastoGs());
            balance.setTotalGastoRs(balanceDto.getTotalGastoRs());
            balance.setTotalGastoDs(balanceDto.getTotalGastoDs());
            balance.setTotalAperGs(balanceDto.getTotalGsAper());
            balance.setTotalAperRs(balanceDto.getTotalRsAper());
            balance.setTotalAperDs(balanceDto.getTotalDsAper());
            balance.setTotalCierreGs(balanceDto.getTotalGsCierre());
            balance.setTotalCierreRs(balanceDto.getTotalRsCierre());
            balance.setTotalCierreDs(balanceDto.getTotalDsCierre());
            balance.setTotalDescuento(balanceDto.getTotalDescuento());
            balance.setTotalAumento(balanceDto.getTotalAumento());
            balance.setTotalCanceladasGs(balanceDto.getTotalCanceladasGs());
            balance.setTotalCanceladasRs(balanceDto.getTotalCanceladasRs());
            balance.setTotalCanceladasDs(balanceDto.getTotalCanceladasDs());
            balance.setDiferenciaGs(balanceDto.getDiferenciaGs());
            balance.setDiferenciaRs(balanceDto.getDiferenciaRs());
            balance.setDiferenciaDs(balanceDto.getDiferenciaDs());
        }
        return balance;
    }

    public PdvCaja findLastByMaletinId(Long id) {
        PdvCaja caja = repository.findFirstByMaletinIdOrderByCreadoEnDesc(id).orElse(null);
        return caja;
    }

    private boolean denominacionContains(ConteoMoneda conteoMoneda, String texto) {
        if (conteoMoneda == null || conteoMoneda.getMonedaBilletes() == null) {
            return false;
        }
        return denominacionContains(conteoMoneda.getMonedaBilletes().getMoneda(), texto);
    }

    private boolean denominacionContains(Moneda moneda, String texto) {
        return moneda != null && moneda.getDenominacion() != null && moneda.getDenominacion().contains(texto);
    }

    private void establecerTotalesApertura(PdvCaja pdvCaja, PdvCajaBalanceDto balance, List<ConteoMoneda> conteoMonedaAperList) {
        Double totalGsAper = 0.0;
        Double totalRsAper = 0.0;
        Double totalDsAper = 0.0;
        for (ConteoMoneda c : conteoMonedaAperList) {
            if (denominacionContains(c, "GUARANI")) {
                totalGsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
            } else if (denominacionContains(c, "REAL")) {
                totalRsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
            } else if (denominacionContains(c, "DOLAR")) {
                totalDsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
            }
        }
        PdvCajaBalanceDto desdeMovimientos = new PdvCajaBalanceDto();
        aplicarTotalesAperturaDesdeMovimientos(pdvCaja, desdeMovimientos);
        balance.setTotalGsAper(elegirTotalMoneda(totalGsAper, desdeMovimientos.getTotalGsAper()));
        balance.setTotalRsAper(elegirTotalMoneda(totalRsAper, desdeMovimientos.getTotalRsAper()));
        balance.setTotalDsAper(elegirTotalMoneda(totalDsAper, desdeMovimientos.getTotalDsAper()));
    }

    private void establecerTotalesCierre(PdvCaja pdvCaja, PdvCajaBalanceDto balance, List<ConteoMoneda> conteoMonedaCierreList) {
        Double totalGsCierre = 0.0;
        Double totalRsCierre = 0.0;
        Double totalDsCierre = 0.0;
        for (ConteoMoneda c : conteoMonedaCierreList) {
            if (denominacionContains(c, "GUARANI")) {
                totalGsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
            } else if (denominacionContains(c, "REAL")) {
                totalRsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
            } else if (denominacionContains(c, "DOLAR")) {
                totalDsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
            }
        }
        PdvCajaBalanceDto desdeMovimientos = new PdvCajaBalanceDto();
        aplicarTotalesCierreDesdeMovimientos(pdvCaja, desdeMovimientos);
        balance.setTotalGsCierre(elegirTotalMoneda(totalGsCierre, desdeMovimientos.getTotalGsCierre()));
        balance.setTotalRsCierre(elegirTotalMoneda(totalRsCierre, desdeMovimientos.getTotalRsCierre()));
        balance.setTotalDsCierre(elegirTotalMoneda(totalDsCierre, desdeMovimientos.getTotalDsCierre()));
    }

    private Double elegirTotalMoneda(Double totalConteo, Double totalMovimiento) {
        double conteo = totalConteo != null ? totalConteo : 0.0;
        double movimiento = totalMovimiento != null ? totalMovimiento : 0.0;
        return conteo > 0.0 ? conteo : movimiento;
    }

    private void aplicarTotalesAperturaDesdeMovimientos(PdvCaja pdvCaja, PdvCajaBalanceDto balance) {
        Double totalGsAper = 0.0;
        Double totalRsAper = 0.0;
        Double totalDsAper = 0.0;
        Long conteoAperturaId = pdvCaja.getConteoApertura().getId();
        for (MovimientoCaja movimientoCaja : movimientoCajaService.findByPdvCajaId(pdvCaja.getId())) {
            if (movimientoCaja.getTipoMovimiento() != PdvCajaTipoMovimiento.CAJA_INICIAL) {
                continue;
            }
            if (movimientoCaja.getReferencia() != null && !movimientoCaja.getReferencia().equals(conteoAperturaId)) {
                continue;
            }
            if (denominacionContains(movimientoCaja.getMoneda(), "GUARANI")) {
                totalGsAper += movimientoCaja.getCantidad();
            } else if (denominacionContains(movimientoCaja.getMoneda(), "REAL")) {
                totalRsAper += movimientoCaja.getCantidad();
            } else if (denominacionContains(movimientoCaja.getMoneda(), "DOLAR")) {
                totalDsAper += movimientoCaja.getCantidad();
            }
        }
        balance.setTotalGsAper(totalGsAper);
        balance.setTotalRsAper(totalRsAper);
        balance.setTotalDsAper(totalDsAper);
    }

    private void aplicarTotalesCierreDesdeMovimientos(PdvCaja pdvCaja, PdvCajaBalanceDto balance) {
        Double totalGsCierre = 0.0;
        Double totalRsCierre = 0.0;
        Double totalDsCierre = 0.0;
        Long conteoCierreId = pdvCaja.getConteoCierre().getId();
        for (MovimientoCaja movimientoCaja : movimientoCajaService.findByPdvCajaId(pdvCaja.getId())) {
            if (movimientoCaja.getTipoMovimiento() != PdvCajaTipoMovimiento.CAJA_FINAL) {
                continue;
            }
            if (movimientoCaja.getReferencia() != null && !movimientoCaja.getReferencia().equals(conteoCierreId)) {
                continue;
            }
            if (denominacionContains(movimientoCaja.getMoneda(), "GUARANI")) {
                totalGsCierre += movimientoCaja.getCantidad();
            } else if (denominacionContains(movimientoCaja.getMoneda(), "REAL")) {
                totalRsCierre += movimientoCaja.getCantidad();
            } else if (denominacionContains(movimientoCaja.getMoneda(), "DOLAR")) {
                totalDsCierre += movimientoCaja.getCantidad();
            }
        }
        balance.setTotalGsCierre(totalGsCierre);
        balance.setTotalRsCierre(totalRsCierre);
        balance.setTotalDsCierre(totalDsCierre);
    }

    @Autowired
    private UsuarioService usuarioService;

    public PdvCaja transferirCaja(Long cajaId, Long usuarioId) throws GraphQLException {
        PdvCaja caja = findById(cajaId).orElse(null);
        if (caja == null) {
            throw new GraphQLException("Caja no encontrada");
        }
        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
        if (usuario == null) {
            throw new GraphQLException("Usuario no encontrado");
        }

        List<PdvCaja> aux = repository.findByUsuarioIdAndActivo(usuarioId, true);
        if (aux.size() > 0) {
            throw new GraphQLException("El usuario destino ya tiene una caja abierta");
        }

        caja.setUsuario(usuario);
        return repository.save(caja);
    }

}