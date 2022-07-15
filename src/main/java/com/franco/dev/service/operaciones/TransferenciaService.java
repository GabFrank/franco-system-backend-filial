package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.repository.operaciones.TransferenciaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TransferenciaService extends CrudService<Transferencia, TransferenciaRepository> {
    private final TransferenciaRepository repository;
    @Autowired
    private MovimientoStockService movimientoStockService;

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
        if (transferencia != null) {
            ok = super.deleteById(id);
        }
        return ok;
    }

    @Override
    public Transferencia save(Transferencia entity) {
        Long idActual = env.getProperty("sucursalId", Long.class);
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        } else {
            Transferencia aux = findById(entity.getId()).orElse(null);
            if (aux != null) {
                if ((aux.getSucursalOrigen().getId() == idActual) && (aux.getEtapa() == EtapaTransferencia.TRANSPORTE_VERIFICACION) && (entity.getEtapa() == EtapaTransferencia.TRANSPORTE_EN_CAMINO)) {
                    movimientoStockService.bajaStockPorTransferencia(entity.getId());
                } else if ((aux.getSucursalDestino().getId() == idActual) && aux.getEtapa() == EtapaTransferencia.RECEPCION_EN_VERIFICACION && entity.getEtapa() == EtapaTransferencia.RECEPCION_CONCLUIDA) {
                    movimientoStockService.altaStockPorTransferencia(entity.getId());
                }
            }
//        personaPublisher.publish(p);
        }
        Transferencia e = super.save(entity);
        return e;
    }
}