package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.repository.administrativo.JornadaRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class JornadaService {

    private final JornadaRepository repository;

    public JornadaRepository getRepository() {
        return repository;
    }

    public List<Jornada> findAll(org.springframework.data.domain.Pageable pageable) {
        return repository.findAll(pageable).getContent();
    }

    public Optional<Jornada> findById(com.franco.dev.domain.EmbebedPrimaryKey id) {
        if (id == null)
            return Optional.empty();
        return repository.findById(id);
    }

    public Optional<Jornada> findByIdAndSucursalId(Long id, Long sucursalId) {
        return findById(new com.franco.dev.domain.EmbebedPrimaryKey(id, sucursalId));
    }

    public Boolean delete(Jornada entity) {
        try {
            repository.delete(entity);
            return true;
        } catch (Exception e) {
            return false;
        }
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

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public Jornada save(Jornada entity) {
        if (entity.getId() == null) {
            Long lastId = repository.findMaxId(entity.getSucursalId());
            long newId = (lastId == null ? 0L : lastId) + 1L;
            if (newId % 2 != 0) {
                newId++;
            }
            entity.setId(newId);
        }
        return repository.save(entity);
    }
}
