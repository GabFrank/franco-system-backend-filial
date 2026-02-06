package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.repository.administrativo.MarcacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@AllArgsConstructor
public class MarcacionService extends CrudService<Marcacion, MarcacionRepository> {

    private final MarcacionRepository repository;
    private final JornadaService jornadaService;

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
        procesarJornada(e);
        return e;
    }

    private void procesarJornada(Marcacion marcacion) {
        try {
            if (marcacion.getFechaEntrada() != null && marcacion.getFechaSalida() == null) {
                Optional<Jornada> existingJornada = jornadaService.findByMarcacionEntradaId(marcacion.getId());
                if (!existingJornada.isPresent()) {
                    Jornada jornada = new Jornada();
                    jornada.setUsuario(marcacion.getUsuario());
                    jornada.setFecha(marcacion.getFechaEntrada().toLocalDate());
                    jornada.setMarcacionEntrada(marcacion);
                    jornada.setEstado(EstadoJornada.INCOMPLETO);
                    jornadaService.save(jornada);
                }
            } else if (marcacion.getFechaEntrada() != null && marcacion.getFechaSalida() != null) {
                Optional<Jornada> jornadaOpt = jornadaService.findByMarcacionEntradaId(marcacion.getId());
                if (jornadaOpt.isPresent()) {
                    Jornada jornada = jornadaOpt.get();
                    jornada.setMarcacionSalida(marcacion);
                    jornada.setEstado(EstadoJornada.NORMAL);

                    long minutos = ChronoUnit.MINUTES.between(
                            marcacion.getFechaEntrada(),
                            marcacion.getFechaSalida());
                    jornada.setMinutosTrabajados(minutos);
                    jornadaService.save(jornada);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}