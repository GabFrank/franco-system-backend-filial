package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.operaciones.TransferenciaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class TransferenciaService extends CrudService<Transferencia, TransferenciaRepository> {
    private final TransferenciaRepository repository;
    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private TransferenciaItemService transferenciaItemService;

    @Autowired
    private Environment env;

    @Override
    public TransferenciaRepository getRepository() {
        return repository;
    }

    public List<Transferencia> findBySucursalOrigenId(Long id) {
        return repository.findBySucursalOrigenId(id);
    }

    public List<Transferencia> findBySucursalDestinoId(Long id) {
        return repository.findBySucursalDestinoId(id);
    }

    public List<Transferencia> findByDate(String start, String end) {
        return repository.findByDate(start, end);
    }

    @Override
    public Boolean deleteById(Long id) {
        Boolean ok = false;
        Transferencia transferencia = findById(id).orElse(null);
        List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaIdAndSucursalId(id);
        List<MovimientoStock> movimientoStockList = new ArrayList<>();
        for(TransferenciaItem ti: transferenciaItemList){
            MovimientoStock ms = movimientoStockService.findByTipoMovimientoAndReferencia(TipoMovimiento.TRANSFERENCIA, ti.getId());
            if(ms!=null){
                movimientoStockList.add(ms);
            }
        }
        if (transferencia != null) {
            ok = super.deleteById(id);
            for(MovimientoStock m: movimientoStockList){
                movimientoStockService.delete(m);
            }
        }
        return ok;
    }

    @Override
    public Transferencia save(Transferencia entity) {
        log.info("Guardando transferenncia");
        Long idActual = env.getProperty("sucursalId", Long.class);

        if (entity.getId() == null) {
            log.info("entity get id es null");
            entity.setCreadoEn(LocalDateTime.now());
        } else {
            log.info("entity no es null");
            Transferencia aux = findById(entity.getId()).orElse(null);
            if (aux != null) {
                log.info("id sucursal: " + idActual);
                log.info("id destino: " + aux.getSucursalDestino().getId());
                log.info("etapa actual: " + aux.getEtapa());
                log.info("edtapa nueva: " + entity.getEtapa());
                log.info("aux no es null");
                if (aux.getSucursalOrigen().getId().equals(idActual) && (aux.getEtapa() == EtapaTransferencia.PRE_TRANSFERENCIA_CREACION) && (entity.getEtapa() == EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN)) {
                    movimientoStockService.bajaStockPorTransferencia(entity.getId());
                    log.info("baja stock");
                } else if (aux.getSucursalDestino().getId().equals(idActual) && aux.getEtapa() == EtapaTransferencia.RECEPCION_EN_VERIFICACION && entity.getEtapa() == EtapaTransferencia.RECEPCION_CONCLUIDA) {
                    movimientoStockService.altaStockPorTransferencia(entity.getId());
                    log.info("alta stock");
                }
            }else {
                log.info("Aux es null");
            }
//        personaPublisher.publish(p);
        }
        Transferencia e = super.save(entity);
        return e;
    }
}