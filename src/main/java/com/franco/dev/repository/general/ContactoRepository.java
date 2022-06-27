package com.franco.dev.repository.general;

import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ContactoRepository extends HelperRepository<Contacto, Long> {

    default Class<Contacto> getEntityClass() {
        return Contacto.class;
    }

//    public List<Contacto> findByDescripcion(String texto);

    @Query("select c from Contacto c " +
            "left join c.persona p " +
            "where UPPER(c.telefono) like %?1% OR UPPER(p.nombre) like %?1% Or UPPER(p.apodo) like %?1%")
    public List<Contacto> findbyTelefonoOrPersonaNombre(String texto);

//    public List<Contacto> findBySubFamiliaId(Long id);

    public List<Contacto> findByPersonaId(Long id);

}