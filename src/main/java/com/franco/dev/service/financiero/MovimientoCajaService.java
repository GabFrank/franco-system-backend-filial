package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.repository.financiero.MovimientoCajaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MovimientoCajaService extends CrudService<MovimientoCaja, MovimientoCajaRepository> {

    private final MovimientoCajaRepository repository;

    @Autowired
    private Environment env;

    @Override
    public MovimientoCajaRepository getRepository() {
        return repository;
    }

//    public List<MovimientoCaja> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

//    public List<MovimientoCaja> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return repository.findByAll(texto);
//    }

    public List<MovimientoCaja> findByPdvCajaId(Long id) {
        return repository.findByPdvCajaIdAndActivo(id, true);
    }

    public Double findTotalVentaByCajaIdAndMonedaId(Long id, Long monedaId) {
        Double total = 0.0;
        total = repository.findTotalVentaByCajaIdAndMonedaId(id, monedaId);
        if (total == null) {
            return 0.0;
        } else {
            return total;
        }
    }

    public List<MovimientoCaja> findByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento tipoMovimiento, Long referencia) {
        return repository.findByTipoMovimientoAndReferencia(tipoMovimiento, referencia);
    }

    public Double totalEnCajaPorCajaIdAndMonedaId(Long cajaId, Long monedaId) {
        Double total = repository.totalEnCajaByCajaIdandMonedaId(cajaId, monedaId);
        return total != null ? total : 0.0;
    }

    @Override
    public MovimientoCaja save(MovimientoCaja entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        MovimientoCaja e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    @Override
    public MovimientoCaja saveAndSend(MovimientoCaja entity, Boolean recibir) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        MovimientoCaja e = super.save(entity);
        return e;
    }
}