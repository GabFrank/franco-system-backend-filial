package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreGastoRepository extends HelperRepository<PreGasto, EmbebedPrimaryKey> {

    @Query(value = "SELECT pg.* FROM financiero.pre_gasto pg " +
            "WHERE pg.funcionario_id = :funcionarioId " +
            "ORDER BY pg.id DESC", nativeQuery = true)
    List<PreGasto> buscarPorFuncionario(@Param("funcionarioId") Long funcionarioId);

    @Query(value = "SELECT pg.* FROM financiero.pre_gasto pg " +
            "WHERE pg.estado = :estado " +
            "ORDER BY pg.id DESC", nativeQuery = true)
    List<PreGasto> buscarPorEstado(@Param("estado") String estado);

    @Query(value = "SELECT pg.* FROM financiero.pre_gasto pg " +
            "WHERE pg.estado = :estado " +
            "AND pg.sucursal_id = :sucursalId " +
            "ORDER BY pg.id DESC", nativeQuery = true)
    List<PreGasto> buscarPorEstadoYSucursal(@Param("estado") String estado, @Param("sucursalId") Long sucursalId);

    @Query(value = "SELECT pg.* FROM financiero.pre_gasto pg " +
            "WHERE (CAST(pg.id AS text) LIKE CONCAT('%', :texto, '%') " +
            "OR UPPER(pg.descripcion) LIKE CONCAT('%', UPPER(:texto), '%')) " +
            "AND (:sucursalId IS NULL OR pg.sucursal_id = :sucursalId) " +
            "ORDER BY pg.id DESC", nativeQuery = true)
    List<PreGasto> buscarPorTexto(@Param("texto") String texto, @Param("sucursalId") Long sucursalId);

    @Query(value = "SELECT max(pg.id) FROM financiero.pre_gasto pg WHERE pg.sucursal_id = :sucursalId", nativeQuery = true)
    Long findMaxId(@Param("sucursalId") Long sucursalId);

    PreGasto findByIdAndSucursalId(Long id, Long sucursalId);
}
