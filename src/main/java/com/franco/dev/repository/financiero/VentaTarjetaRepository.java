package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VentaTarjetaRepository extends HelperRepository<VentaTarjeta, Long> {

    default Class<VentaTarjeta> getEntityClass() {
        return VentaTarjeta.class;
    }

    VentaTarjeta findByIdAndSucursalId(Long id, Long sucursalId);

    VentaTarjeta findFirstByVentaIdAndSucursalIdAndEstadoOrderByIdAsc(Long ventaId, Long sucursalId, String estado);

    VentaTarjeta findFirstByVentaIdAndSucursalIdOrderByIdAsc(Long ventaId, Long sucursalId);

    List<VentaTarjeta> findByVentaIdAndSucursalIdOrderByIdAsc(Long ventaId, Long sucursalId);

    List<VentaTarjeta> findByCajaIdAndSucursalIdOrderByCreadoEnDesc(Long cajaId, Long sucursalId);

    List<VentaTarjeta> findByCajaIdAndSucursalIdAndEstado(Long cajaId, Long sucursalId, String estado);

    Long countByCajaIdAndSucursalIdAndEstado(Long cajaId, Long sucursalId, String estado);

    /** Un cupon solo puede estar registrado una vez: sirve para detectar el re-escaneo. */
    List<VentaTarjeta> findByQrCrudo(String qrCrudo);
}
