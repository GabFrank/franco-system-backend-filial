package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@AllArgsConstructor
public class HorarioService extends CrudService<Horario, HorarioRepository> {

    private final HorarioRepository repository;

    @Override
    public HorarioRepository getRepository() {
        return repository;
    }

    public List<Horario> findByUsuarioId(Long usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }
}
