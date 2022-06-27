package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.MovimientoCajaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MovimientoCajaService extends CrudService<MovimientoCaja, MovimientoCajaRepository> {

    private final MovimientoCajaRepository repository;

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

    public List<MovimientoCaja> findByPdvCajaId(Long id){
        return repository.findByPdvCajaIdAndActivo(id, true);
    }

    public Double findTotalVentaByCajaIdAndMonedaId(Long id, Long monedaId){
        Double total = 0.0;
        total = repository.findTotalVentaByCajaIdAndMonedaId(id, monedaId);
        if(total == null){
            return 0.0;
        } else {
            return total;
        }
    }

    public MovimientoCaja findByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento tipoMovimiento, Long referencia){
        return repository.findByTipoMovimientoAndReferencia(tipoMovimiento, referencia);
    }

    public Double totalEnCajaPorCajaIdAndMonedaId(Long cajaId, Long monedaId){
        return repository.totalEnCajaByCajaIdandMonedaId(cajaId, monedaId);
    }

    @Override
    public MovimientoCaja save(MovimientoCaja entity) {
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        MovimientoCaja e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}