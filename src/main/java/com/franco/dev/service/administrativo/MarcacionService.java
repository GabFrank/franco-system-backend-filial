package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.repository.administrativo.MarcacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MarcacionService extends CrudService<Marcacion, MarcacionRepository> {

    private final MarcacionRepository repository;

    @Override
    public MarcacionRepository getRepository() {
        return repository;
    }

    public List<Marcacion> findByUsuarioId(Long usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    public List<Marcacion> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin) {
        return repository.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin);
    }

    public List<Marcacion> findBySucursalEntradaId(Long sucursalId) {
        return repository.findBySucursalEntradaId(sucursalId);
    }

    public List<Marcacion> findBySucursalSalidaId(Long sucursalId) {
        return repository.findBySucursalSalidaId(sucursalId);
    }

    @Override
    public Marcacion save(Marcacion entity) {
        if (entity.getId() == null) {
            if (entity.getFechaEntrada() == null && entity.getFechaSalida() == null) {
                entity.setFechaEntrada(LocalDateTime.now());
            }
        }
        Marcacion e = super.save(entity);
        return e;
    }
}