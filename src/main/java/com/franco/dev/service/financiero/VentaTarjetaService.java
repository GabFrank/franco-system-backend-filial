package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.repository.financiero.VentaTarjetaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class VentaTarjetaService extends CrudService<VentaTarjeta, VentaTarjetaRepository> {

    private final VentaTarjetaRepository repository;

    @Override
    public VentaTarjetaRepository getRepository() {
        return repository;
    }

    public VentaTarjeta findByIdAndSucursalId(Long id, Long sucursalId) {
        return repository.findByIdAndSucursalId(id, sucursalId);
    }

    public VentaTarjeta findByVentaIdAndSucursalId(Long ventaId, Long sucursalId) {
        VentaTarjeta pendiente = repository.findFirstByVentaIdAndSucursalIdAndEstadoOrderByIdAsc(ventaId, sucursalId, "PENDIENTE");
        return pendiente != null ? pendiente : repository.findFirstByVentaIdAndSucursalIdOrderByIdAsc(ventaId, sucursalId);
    }

    public List<VentaTarjeta> findAllByVentaIdAndSucursalId(Long ventaId, Long sucursalId) {
        return repository.findByVentaIdAndSucursalIdOrderByIdAsc(ventaId, sucursalId);
    }

    public List<VentaTarjeta> findByCajaIdAndSucursalId(Long cajaId, Long sucursalId) {
        return repository.findByCajaIdAndSucursalIdOrderByCreadoEnDesc(cajaId, sucursalId);
    }

    public Long countVentasTarjetaSinRegistrar(Long cajaId, Long sucursalId) {
        return repository.countByCajaIdAndSucursalIdAndEstado(cajaId, sucursalId, "PENDIENTE");
    }

    /**
     * Cierre de caja con pendientes confirmado por el cajero: los registros
     * PENDIENTE de la caja pasan a NO_COMPLETADO (estado terminal, auditable).
     * El cambio replica al central via BRANCH_TO_MAIN.
     */
    public int marcarNoCompletadas(Long cajaId, Long sucursalId) {
        List<VentaTarjeta> pendientes = repository.findByCajaIdAndSucursalIdAndEstado(cajaId, sucursalId, "PENDIENTE");
        pendientes.forEach(vt -> {
            vt.setEstado("NO_COMPLETADO");
            repository.save(vt);
        });
        return pendientes.size();
    }
}
