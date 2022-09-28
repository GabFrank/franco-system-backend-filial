package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Service
@AllArgsConstructor
public class GastoService extends CrudService<Gasto, GastoRepository> {

    private final GastoRepository repository;
    @Autowired
    private MonedaService monedaService;
    @Autowired
    private MovimientoCajaService movimientoCajaService;
    @Autowired
    private CambioService cambioService;

    @Override
    public GastoRepository getRepository() {
        return repository;
    }

//    public List<Gasto> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<Gasto> findByDate(String inicio, String fin) {
        return repository.findByCreadoEnBetween(toDate(inicio), toDate(fin));
    }

    public List<Gasto> findByCajaId(Long id) {
        return repository.findByCajaId(id);
    }

    @Override
    public Gasto save(Gasto entity) {
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        Gasto e = super.save(entity);
        List<Moneda> monedaList = monedaService.findAll2();
        MovimientoCaja movimientoCaja = new MovimientoCaja();
        movimientoCaja.setTipoMovimiento(PdvCajaTipoMovimiento.GASTO);
        // guarani

        if (e.getFinalizado() == true) {
            if (e.getRetiroGs() > 0) {
                movimientoCaja.setCantidad(e.getRetiroGs() * -1);
                movimientoCaja.setUsuario(e.getUsuario());
                movimientoCaja.setMoneda(monedaService.findByDescripcion("GUARANI"));
                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
                movimientoCaja.setPdvCaja(e.getCaja());
                movimientoCaja.setReferencia(e.getId());
                movimientoCaja.setCreadoEn(e.getCreadoEn());
                movimientoCajaService.save(movimientoCaja);
            }

            //real
            if (e.getRetiroRs() > 0) {
                movimientoCaja.setCantidad(e.getRetiroRs() * -1);
                movimientoCaja.setUsuario(e.getUsuario());
                movimientoCaja.setMoneda(monedaService.findByDescripcion("REAL"));
                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
                movimientoCaja.setPdvCaja(e.getCaja());
                movimientoCaja.setReferencia(e.getId());
                movimientoCaja.setCreadoEn(e.getCreadoEn());
                movimientoCajaService.save(movimientoCaja);
            }
            // dolar
            if (e.getRetiroDs() > 0) {
                movimientoCaja.setCantidad(e.getRetiroDs() * -1);
                movimientoCaja.setUsuario(e.getUsuario());
                movimientoCaja.setMoneda(monedaService.findByDescripcion("DOLAR"));
                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
                movimientoCaja.setPdvCaja(e.getCaja());
                movimientoCaja.setReferencia(e.getId());
                movimientoCaja.setCreadoEn(e.getCreadoEn());
                movimientoCajaService.save(movimientoCaja);
            }
            if (e.getVueltoGs() > 0) {
                movimientoCaja.setCantidad(e.getVueltoGs());
                movimientoCaja.setUsuario(e.getUsuario());
                movimientoCaja.setMoneda(monedaService.findByDescripcion("GUARANI"));
                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
                movimientoCaja.setPdvCaja(e.getCaja());
                movimientoCaja.setReferencia(e.getId());
                movimientoCaja.setCreadoEn(e.getCreadoEn());
                movimientoCajaService.save(movimientoCaja);
            }

            //real
            if (e.getVueltoRs() > 0) {
                movimientoCaja.setCantidad(e.getVueltoRs());
                movimientoCaja.setUsuario(e.getUsuario());
                movimientoCaja.setMoneda(monedaService.findByDescripcion("REAL"));
                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
                movimientoCaja.setPdvCaja(e.getCaja());
                movimientoCaja.setReferencia(e.getId());
                movimientoCaja.setCreadoEn(e.getCreadoEn());
                movimientoCajaService.save(movimientoCaja);
            }
            // dolar
            if (e.getVueltoDs() > 0) {
                movimientoCaja.setCantidad(e.getVueltoDs());
                movimientoCaja.setUsuario(e.getUsuario());
                movimientoCaja.setMoneda(monedaService.findByDescripcion("DOLAR"));
                movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
                movimientoCaja.setPdvCaja(e.getCaja());
                movimientoCaja.setReferencia(e.getId());
                movimientoCaja.setCreadoEn(e.getCreadoEn());
                movimientoCajaService.save(movimientoCaja);
            }
        }

//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public Gasto saveAndSend(Gasto entity, Boolean recibir) {
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        Boolean isNew = entity.getId() == null;
        if (isNew) {
            if (entity.getRetiroGs() > 0) {
                Double totalEnCajaGs = movimientoCajaService.totalEnCajaPorCajaIdAndMonedaId(entity.getCaja().getId(), Long.valueOf(1));
                if (entity.getRetiroGs() > totalEnCajaGs) {
                    throw new GraphQLException("El valor de gasto es mayor al total en caja: Guaraiens");
                }
            }
            if (entity.getRetiroRs() > 0) {
                Double totalEnCajaRs = movimientoCajaService.totalEnCajaPorCajaIdAndMonedaId(entity.getCaja().getId(), Long.valueOf(2));
                if (entity.getRetiroRs() > totalEnCajaRs) {
                    throw new GraphQLException("El valor de gasto es mayor al total en caja: Reales");
                }
            }
            if (entity.getRetiroDs() > 0) {
                Double totalEnCajaDs = movimientoCajaService.totalEnCajaPorCajaIdAndMonedaId(entity.getCaja().getId(), Long.valueOf(3));
                if (entity.getRetiroDs() > totalEnCajaDs) {
                    throw new GraphQLException("El valor de gasto es mayor al total en caja: Dolares");
                }
            }

        }
        Gasto e = super.save(entity);
        propagacionService.propagarEntidad(e, TipoEntidad.GASTO, recibir);
        List<Moneda> monedaList = monedaService.findAll2();
        MovimientoCaja movimientoCaja = new MovimientoCaja();
        movimientoCaja.setTipoMovimiento(PdvCajaTipoMovimiento.GASTO);
        movimientoCaja.setSucursalId(e.getSucursalId());
        // guarani

        if (isNew == true) {
            movimientoCaja.setCantidad((e.getRetiroGs() > 0 ? e.getRetiroGs() : 0.0) * -1);
            movimientoCaja.setUsuario(e.getUsuario());
            movimientoCaja.setMoneda(monedaService.findByDescripcion("GUARANI"));
            movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
            movimientoCaja.setPdvCaja(e.getCaja());
            movimientoCaja.setReferencia(e.getId());
            movimientoCaja.setCreadoEn(e.getCreadoEn());
            if (movimientoCaja.getCantidad() != 0.0) movimientoCajaService.saveAndSend(movimientoCaja, false);


            //real
            movimientoCaja.setCantidad((e.getRetiroRs() > 0 ? e.getRetiroRs() : 0.0) * -1);
            movimientoCaja.setUsuario(e.getUsuario());
            movimientoCaja.setMoneda(monedaService.findByDescripcion("REAL"));
            movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
            movimientoCaja.setPdvCaja(e.getCaja());
            movimientoCaja.setReferencia(e.getId());
            movimientoCaja.setCreadoEn(e.getCreadoEn());
            if (movimientoCaja.getCantidad() != 0.0) movimientoCajaService.saveAndSend(movimientoCaja, false);

            // dolar
            movimientoCaja.setCantidad((e.getRetiroDs() > 0 ? e.getRetiroDs() : 0.0) * -1);
            movimientoCaja.setUsuario(e.getUsuario());
            movimientoCaja.setMoneda(monedaService.findByDescripcion("DOLAR"));
            movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
            movimientoCaja.setPdvCaja(e.getCaja());
            movimientoCaja.setReferencia(e.getId());
            movimientoCaja.setCreadoEn(e.getCreadoEn());
            if (movimientoCaja.getCantidad() != 0.0) movimientoCajaService.saveAndSend(movimientoCaja, false);

            movimientoCaja.setCantidad(e.getVueltoGs());
            movimientoCaja.setUsuario(e.getUsuario());
            movimientoCaja.setMoneda(monedaService.findByDescripcion("GUARANI"));
            movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
            movimientoCaja.setPdvCaja(e.getCaja());
            movimientoCaja.setReferencia(e.getId());
            movimientoCaja.setCreadoEn(e.getCreadoEn());
            if (movimientoCaja.getCantidad() != 0.0) movimientoCajaService.saveAndSend(movimientoCaja, false);


            //real
            movimientoCaja.setCantidad(e.getVueltoRs());
            movimientoCaja.setUsuario(e.getUsuario());
            movimientoCaja.setMoneda(monedaService.findByDescripcion("REAL"));
            movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
            movimientoCaja.setPdvCaja(e.getCaja());
            movimientoCaja.setReferencia(e.getId());
            movimientoCaja.setCreadoEn(e.getCreadoEn());
            if (movimientoCaja.getCantidad() != 0.0) movimientoCajaService.saveAndSend(movimientoCaja, false);

            // dolar
            movimientoCaja.setCantidad(e.getVueltoDs());
            movimientoCaja.setUsuario(e.getUsuario());
            movimientoCaja.setMoneda(monedaService.findByDescripcion("DOLAR"));
            movimientoCaja.setCambio(cambioService.findLastByMonedaId(movimientoCaja.getMoneda().getId()));
            movimientoCaja.setPdvCaja(e.getCaja());
            movimientoCaja.setReferencia(e.getId());
            movimientoCaja.setCreadoEn(e.getCreadoEn());
            if (movimientoCaja.getCantidad() != 0.0) movimientoCajaService.saveAndSend(movimientoCaja, false);

        } else {
            List<MovimientoCaja> movimientoCajaList = movimientoCajaService.findByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento.GASTO, entity.getId());
            for (MovimientoCaja mc : movimientoCajaList) {

                if (mc.getMoneda().getDenominacion().contains("GUARANI")) {
                    mc.setCantidad(e.getRetiroGs() + e.getVueltoGs());
                } else if (mc.getMoneda().getDenominacion().contains("REAL")) {
                    mc.setCantidad(e.getRetiroRs() + e.getVueltoRs());
                } else if (mc.getMoneda().getDenominacion().contains("DOLAR")) {
                    mc.setCantidad(e.getRetiroDs() + e.getVueltoRs());
                }

                movimientoCajaService.saveAndSend(mc, false);
            }
        }
//        personaPublisher.publish(p);
        return e;
    }
}