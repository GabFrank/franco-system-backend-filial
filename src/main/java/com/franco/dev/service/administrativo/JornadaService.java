package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.repository.administrativo.JornadaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class JornadaService extends CrudService<Jornada, JornadaRepository> {

    private final JornadaRepository repository;

    @Override
    public JornadaRepository getRepository() {
        return repository;
    }

    public List<Jornada> findByUsuarioId(Long usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    public List<Jornada> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin) {
        return repository.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin);
    }

    public List<Jornada> findByFechaRange(String fechaInicio, String fechaFin) {
        return repository.findByFechaRange(fechaInicio, fechaFin);
    }

    public List<Jornada> findByUsuarioIdAndFecha(Long usuarioId, String fecha) {
        return repository.findByUsuarioIdAndFecha(usuarioId, fecha);
    }

    public Optional<Jornada> findByMarcacionEntradaId(Long id) {
        return repository.findByMarcacionEntradaId(id);
    }

    @Override
    public Jornada save(Jornada entity) {
        Jornada e = super.save(entity);
        return e;
    }
}
