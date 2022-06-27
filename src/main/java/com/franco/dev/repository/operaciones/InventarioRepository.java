package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InventarioRepository extends HelperRepository<Inventario, Long> {

    default Class<Inventario> getEntityClass() {
        return Inventario.class;
    }

    @Query(value = "select * from operaciones.inventario ms \n" +
            "where ms.fecha_inicio between cast(?1 as timestamp) and cast(?2 as timestamp) or ms.estado = 'ABIERTO'", nativeQuery = true)
    public List<Inventario> findByDate(String inicio, String fin);

    @Query(value = "select distinct on (i.id) i.id, i.id_central, i.id_origen, i.sucursal_id, i.fecha_inicio, i.fecha_fin, i.abierto, i.tipo, i.estado, i.usuario_id, i.observacion  from operaciones.inventario i " +
            "left join operaciones.inventario_producto ip on i.id = ip.inventario_id " +
            "where ip.usuario_id = ?1 order by i.id", nativeQuery = true)
    public List<Inventario> findByUsuarioId(Long id);

}