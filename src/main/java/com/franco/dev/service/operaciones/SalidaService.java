package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.operaciones.EntradaRepository;
import com.franco.dev.repository.operaciones.SalidaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class SalidaService extends CrudService<Salida, SalidaRepository> {
    private final Logger log = LoggerFactory.getLogger(EntradaService.class);

    private final SalidaRepository repository;

    @Autowired
    private SalidaItemService salidaItemService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Override
    public SalidaRepository getRepository() {
        return repository;
    }

    @Override
    public Salida save(Salida entity) {
        if(entity.getCreadoEn()!=null){
            log.warn(entity.getCreadoEn().toString());
        }
        if(entity.getCreadoEn()==null) entity.setCreadoEn(LocalDateTime.now());
        Salida e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public List<Salida> findByDate(String inicio, String fin){
        return repository.findByDate(inicio, fin);
    }

    @Transactional
    public Boolean finalizarSalida(Long id){
        Boolean ok = true;
        Salida salida = findById(id).orElse(null);
        List<SalidaItem> salidaItemList = new ArrayList<>();
        if(salida!=null){
            salidaItemList = salidaItemService.findBySalidaId(salida.getId());
            if(!salidaItemList.isEmpty()){
                for(SalidaItem e: salidaItemList){
                    MovimientoStock movimientoStock = new MovimientoStock();
                    movimientoStock.setCantidad(e.getCantidad() * e.getPresentacion().getCantidad() * -1);
                    movimientoStock.setProducto(e.getProducto());
                    movimientoStock.setEstado(true);
                    movimientoStock.setReferencia(id);
                    movimientoStock.setTipoMovimiento(TipoMovimiento.SALIDA);
                    movimientoStock.setCreadoEn(LocalDateTime.now());
                    movimientoStock.setUsuario(e.getUsuario());
                    MovimientoStock newMovimiento = movimientoStockService.save(movimientoStock);
                    if(newMovimiento.getId()==null){
                        ok = false;
                    }
                }
            }
            salida.setActivo(ok);
            save(salida);
        }
        return ok;
    }
}