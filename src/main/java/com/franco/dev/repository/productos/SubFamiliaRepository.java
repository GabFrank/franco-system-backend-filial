package com.franco.dev.repository.productos;


import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SubFamiliaRepository extends HelperRepository<Subfamilia, Long> {

    default Class<Subfamilia> getEntityClass() {
        return Subfamilia.class;
    }

    public List<Subfamilia> findByDescripcion(String texto);

    public List<Subfamilia> findByFamiliaDescripcion(String texto);

    public List<Subfamilia> findByFamiliaId(Long id);

    public List<Subfamilia> findBySubfamiliaId(Long id);

//    @Override
//    @Query(value = "select * from productos.subfamilia s " +
//            "where s.sub_familia_id is null", nativeQuery = true)
    public List<Subfamilia> findBySubfamiliaIsNull();

}
