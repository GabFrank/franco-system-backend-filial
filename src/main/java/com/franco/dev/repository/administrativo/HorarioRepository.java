package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface HorarioRepository extends HelperRepository<Horario, Long> {

    @Query("select h from Horario h join h.dias d where h.usuario.id = ?1 and (d = ?2 or d = com.franco.dev.domain.administrativo.enums.Dia.TODOS)")
    Horario findByUsuarioIdAndDia(Long usuarioId, com.franco.dev.domain.administrativo.enums.Dia dia);

    List<Horario> findByUsuarioId(Long usuarioId);
}
