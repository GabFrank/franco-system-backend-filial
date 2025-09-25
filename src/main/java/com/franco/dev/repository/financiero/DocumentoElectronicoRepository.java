package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentoElectronicoRepository extends JpaRepository<DocumentoElectronico, Long> {
    
    DocumentoElectronico findByFacturaLegalIdAndSucursalId(Long facturaLegalId, Long sucursalId);
    
    Optional<DocumentoElectronico> findByCdc(String cdc);
    
    List<DocumentoElectronico> findByEstado(EstadoDE estado);
    
    List<DocumentoElectronico> findByLoteDe(com.franco.dev.domain.financiero.LoteDE loteDe);
    
    List<DocumentoElectronico> findBySucursalIdAndActivoTrue(Long sucursalId);
}
