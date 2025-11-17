package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TimbradoRepository extends HelperRepository<Timbrado, Long> {

    default Class<Timbrado> getEntityClass() {
        return Timbrado.class;
    }

    /**
     * Busca el primer timbrado que esté activo y sea electrónico.
     * Este método se utiliza para obtener el timbrado que se usará en la configuración de SIFEN.
     *
     * @return Optional con el timbrado activo y electrónico, o vacío si no se encuentra
     */
    @Query("SELECT t FROM Timbrado t WHERE t.activo = true AND t.isElectronico = true ORDER BY t.id DESC")
    Optional<Timbrado> findFirstByActivoTrueAndIsElectronicoTrueOrderByIdDesc();

}