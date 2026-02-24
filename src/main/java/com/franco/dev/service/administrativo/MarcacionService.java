package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.repository.administrativo.MarcacionRepository;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import com.franco.dev.domain.administrativo.enums.Dia;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Service
@AllArgsConstructor
public class MarcacionService extends CrudService<Marcacion, MarcacionRepository> {

    private final MarcacionRepository repository;
    private final JornadaService jornadaService;
    private final com.franco.dev.service.personas.FuncionarioService funcionarioService;
    private final HorarioRepository horarioRepository;

    @Override
    public MarcacionRepository getRepository() {
        return repository;
    }

    public Page<Marcacion> findAllPaged(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAll(pageable);
    }

    public Page<Marcacion> findByUsuarioId(Long usuarioId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByUsuarioId(usuarioId, pageable);
    }

    public Page<Marcacion> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin,
            Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin, pageable);
    }

    public Page<Marcacion> findByFechaRange(String fechaInicio, String fechaFin, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByFechaRange(fechaInicio, fechaFin, pageable);
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

        // Aseguramos que el tipo esté seteado antes de procesar
        if (entity.getTipo() == null) {
            if (entity.getFechaSalida() != null) {
                entity.setTipo(com.franco.dev.domain.administrativo.enums.TipoMarcacion.SALIDA);
            } else {
                entity.setTipo(com.franco.dev.domain.administrativo.enums.TipoMarcacion.ENTRADA);
            }
        }

        Marcacion e = super.save(entity);
        if (e.getUsuario() == null && entity.getUsuario() != null) {
            e.setUsuario(entity.getUsuario());
        }

        procesarJornada(e);
        return e;
    }

    private void procesarJornada(Marcacion marcacion) {
        try {
            if (marcacion.getUsuario() == null || marcacion.getUsuario().getId() == null) {
                return;
            }

            LocalDateTime fechaReferencia = marcacion.getFechaEntrada();
            if (fechaReferencia == null)
                fechaReferencia = marcacion.getFechaSalida();
            if (fechaReferencia == null)
                fechaReferencia = LocalDateTime.now();

            int dayOfWeekValue = fechaReferencia.getDayOfWeek().getValue();
            Dia diaSemana = Dia.values()[dayOfWeekValue];

            Horario horario = null;

            com.franco.dev.domain.personas.Funcionario funcionario = null;
            if (marcacion.getUsuario() != null) {
                if (marcacion.getUsuario().getPersona() != null) {
                    funcionario = funcionarioService.findByPersonaId(marcacion.getUsuario().getPersona().getId());
                } else {
                    funcionario = funcionarioService.findByUsuarioId(marcacion.getUsuario().getId());
                }
            }

            if (funcionario != null) {

                if (funcionario.getHorario() != null) {
                    horario = funcionario.getHorario();

                    if (horario.getDias() != null && !horario.getDias().isEmpty() && diaSemana != null) {
                        if (!horario.getDias().contains(diaSemana) && !horario.getDias().contains(Dia.TODOS)) {
                            List<Horario> horariosUsuario = horarioRepository
                                    .findByUsuarioId(marcacion.getUsuario().getId());
                            if (horariosUsuario != null && !horariosUsuario.isEmpty()) {
                                Horario alternativo = null;
                                for (Horario h : horariosUsuario) {
                                    if (h.getDias() != null
                                            && (h.getDias().contains(diaSemana) || h.getDias().contains(Dia.TODOS))
                                            && h.getHoraEntrada() != null) {
                                        alternativo = h;
                                        break;
                                    }
                                }
                                if (alternativo == null) {
                                    for (Horario h : horariosUsuario) {
                                        if (h.getHoraEntrada() != null) {
                                            alternativo = h;
                                            break;
                                        }
                                    }
                                }
                                if (alternativo != null) {
                                    horario = alternativo;
                                }
                            }
                        }
                    }
                } else {

                    horario = null;
                }
            } else {

                if (marcacion.getUsuario() != null && diaSemana != null) {
                    horario = horarioRepository.findByUsuarioIdAndDia(marcacion.getUsuario().getId(), diaSemana);
                }
            }

            java.time.LocalDate fechaJornada = fechaReferencia.toLocalDate();

            if (marcacion.getTipo() == com.franco.dev.domain.administrativo.enums.TipoMarcacion.SALIDA) {
                java.time.LocalDate ayer = fechaJornada.minusDays(1);
                List<Jornada> jornadasAyer = jornadaService.findByUsuarioIdAndFecha(
                        marcacion.getUsuario().getId(),
                        ayer.toString());
                if (jornadasAyer != null && !jornadasAyer.isEmpty()) {
                    Jornada ultimaAyer = jornadasAyer.get(jornadasAyer.size() - 1);

                    if (ultimaAyer.getEstado() == EstadoJornada.INCOMPLETO) {
                        if (ultimaAyer.getTurno() == com.franco.dev.domain.administrativo.enums.Turno.MADRUGADA) {
                            fechaJornada = ayer;
                        }
                    }
                }
            }

            List<Jornada> jornadas = jornadaService.findByUsuarioIdAndFecha(
                    marcacion.getUsuario().getId(),
                    fechaJornada.toString());

            Jornada jornada = null;
            if (jornadas != null && !jornadas.isEmpty()) {
                Jornada lastJornada = jornadas.get(jornadas.size() - 1);
                boolean crearNueva = false;

                if (lastJornada.getEstado() == EstadoJornada.NORMAL || lastJornada.getMarcacionSalida() != null) {

                    if (marcacion.getTipo() == com.franco.dev.domain.administrativo.enums.TipoMarcacion.ENTRADA
                            || marcacion.getFechaEntrada() != null) {
                        crearNueva = true;
                    } else {
                        jornada = lastJornada;
                    }
                } else {
                    jornada = lastJornada;
                }

                if (crearNueva) {
                    jornada = new Jornada();
                    jornada.setUsuario(marcacion.getUsuario());
                    jornada.setFecha(fechaJornada);
                    jornada.setEstado(EstadoJornada.INCOMPLETO);
                }
            } else {
                jornada = new Jornada();
                jornada.setUsuario(marcacion.getUsuario());
                jornada.setFecha(fechaJornada);
                jornada.setEstado(EstadoJornada.INCOMPLETO);
            }

            if (horario != null && jornada.getHoraEntradaHorario() == null) {
                jornada.setTurno(horario.getTurno());
                jornada.setHoraEntradaHorario(horario.getHoraEntrada());
                jornada.setHoraSalidaHorario(horario.getHoraSalida());
                jornada.setInicioDescansoHorario(horario.getInicioDescanso());
                jornada.setFinDescansoHorario(horario.getFinDescanso());
                jornada.setToleranciaMinutosHorario(horario.getToleranciaMinutos());
            }

            Marcacion marcacionEntradaActual = null;

            if (marcacion.getTipo() == com.franco.dev.domain.administrativo.enums.TipoMarcacion.ENTRADA) {
                if (jornada.getMarcacionEntrada() == null) {
                    jornada.setMarcacionEntrada(marcacion);
                    marcacionEntradaActual = marcacion;
                } else if (jornada.getMarcacionSalidaAlmuerzo() != null
                        && jornada.getMarcacionEntradaAlmuerzo() == null) {
                    jornada.setMarcacionEntradaAlmuerzo(marcacion);
                } else {
                    // Si ya tiene entrada, tal vez estamos re-procesando o actualizando
                    if (jornada.getMarcacionEntrada().getId().equals(marcacion.getId())) {
                        marcacionEntradaActual = marcacion;
                    }
                }
            } else if (marcacion.getTipo() == com.franco.dev.domain.administrativo.enums.TipoMarcacion.SALIDA) {
                if (jornada.getMarcacionEntrada() != null && jornada.getMarcacionSalidaAlmuerzo() == null) {
                    Boolean esAlmuerzo = marcacion.getEsSalidaAlmuerzo();
                    if (Boolean.TRUE.equals(esAlmuerzo)) {
                        jornada.setMarcacionSalidaAlmuerzo(marcacion);
                    } else {
                        jornada.setMarcacionSalida(marcacion);
                        jornada.setEstado(EstadoJornada.NORMAL);
                    }
                } else if (jornada.getMarcacionEntradaAlmuerzo() != null && jornada.getMarcacionSalida() == null) {
                    jornada.setMarcacionSalida(marcacion);
                    jornada.setEstado(EstadoJornada.NORMAL);
                } else if (jornada.getMarcacionEntrada() == null) {
                    // Caso excepcional: marca salida sin haber marcado entrada
                    jornada.setMarcacionEntrada(marcacion);
                    jornada.setMarcacionSalida(marcacion);
                    jornada.setEstado(EstadoJornada.NORMAL);
                }
            }

            long minutosTrabajados = 0;
            long minutosExtras = 0;
            Marcacion entradaParaCalculo = marcacionEntradaActual != null ? marcacionEntradaActual
                    : jornada.getMarcacionEntrada();

            if (entradaParaCalculo != null && entradaParaCalculo.getFechaEntrada() != null) {
                long minutosLlegadaTardiaTotal = 0;
                long llegadaTardiaAlmuerzo = 0;
                long minutosDescanso = 60;
                long descansoADescontar = 60;

                java.time.LocalTime horaEntradaHorario = jornada.getHoraEntradaHorario();
                java.time.LocalTime horaSalidaHorario = jornada.getHoraSalidaHorario();
                java.time.LocalTime inicioDescansoHorario = jornada.getInicioDescansoHorario();
                java.time.LocalTime finDescansoHorario = jornada.getFinDescansoHorario();

                if (horaEntradaHorario != null) {
                    java.time.LocalTime horaEntradaReal = entradaParaCalculo.getFechaEntrada().toLocalTime();
                    long diff = ChronoUnit.MINUTES.between(horaEntradaHorario, horaEntradaReal);
                    if (diff > 0) {
                        minutosLlegadaTardiaTotal = diff;

                    } else {

                    }
                } else {

                }

                if (inicioDescansoHorario != null && finDescansoHorario != null) {
                    minutosDescanso = ChronoUnit.MINUTES.between(inicioDescansoHorario,
                            finDescansoHorario);
                    if (minutosDescanso < 0)
                        minutosDescanso += 1440;
                }

                long tiempoAlmuerzoReal = -1;
                if (jornada.getMarcacionSalidaAlmuerzo() != null && jornada.getMarcacionEntradaAlmuerzo() != null) {
                    LocalDateTime salidaAlmuerzo = jornada.getMarcacionSalidaAlmuerzo().getFechaSalida() != null
                            ? jornada.getMarcacionSalidaAlmuerzo().getFechaSalida()
                            : jornada.getMarcacionSalidaAlmuerzo().getFechaEntrada();
                    LocalDateTime entradaAlmuerzo = jornada.getMarcacionEntradaAlmuerzo().getFechaEntrada();
                    if (entradaAlmuerzo.isAfter(salidaAlmuerzo)) {
                        tiempoAlmuerzoReal = ChronoUnit.MINUTES.between(salidaAlmuerzo, entradaAlmuerzo);
                    }
                }

                descansoADescontar = minutosDescanso;
                if (tiempoAlmuerzoReal > 0) {
                    if (tiempoAlmuerzoReal > minutosDescanso) {
                        llegadaTardiaAlmuerzo = (tiempoAlmuerzoReal - minutosDescanso);
                    }
                    descansoADescontar = Math.max(minutosDescanso, tiempoAlmuerzoReal);
                }

                jornada.setMinutosLlegadaTardia(minutosLlegadaTardiaTotal);
                jornada.setMinutosLlegadaTardiaAlmuerzo(llegadaTardiaAlmuerzo);

                LocalDateTime entradaReal = entradaParaCalculo.getFechaEntrada();
                LocalDateTime salidaReal = null;

                if (jornada.getMarcacionSalida() != null) {
                    salidaReal = jornada.getMarcacionSalida().getFechaSalida() != null
                            ? jornada.getMarcacionSalida().getFechaSalida()
                            : jornada.getMarcacionSalida().getFechaEntrada();
                } else if (jornada.getMarcacionEntradaAlmuerzo() != null) {
                    salidaReal = jornada.getMarcacionEntradaAlmuerzo().getFechaEntrada();
                } else if (jornada.getMarcacionSalidaAlmuerzo() != null) {
                    salidaReal = jornada.getMarcacionSalidaAlmuerzo().getFechaSalida() != null
                            ? jornada.getMarcacionSalidaAlmuerzo().getFechaSalida()
                            : jornada.getMarcacionSalidaAlmuerzo().getFechaEntrada();
                }

                if (salidaReal != null) {
                    if (horaEntradaHorario != null && horaSalidaHorario != null) {
                        LocalDateTime hEntradaHorario = entradaReal.toLocalDate().atTime(horaEntradaHorario);
                        LocalDateTime hSalidaHorario = entradaReal.toLocalDate().atTime(horaSalidaHorario);
                        if (hSalidaHorario.isBefore(hEntradaHorario)) {
                            hSalidaHorario = hSalidaHorario.plusDays(1);
                        }

                        LocalDateTime entradaCalculo = entradaReal;
                        if (entradaReal.isBefore(hEntradaHorario)) {
                            long diffEntrada = ChronoUnit.MINUTES.between(entradaReal, hEntradaHorario);
                            if (diffEntrada <= 40) {
                                entradaCalculo = hEntradaHorario;
                            }
                        }

                        long totalMinutos = ChronoUnit.MINUTES.between(entradaCalculo, salidaReal);

                        if (totalMinutos > (5 * 60) || tiempoAlmuerzoReal >= 0) {
                            totalMinutos -= descansoADescontar;
                        }
                        if (totalMinutos < 0)
                            totalMinutos = 0;

                        long horasProgramadas = ChronoUnit.MINUTES.between(hEntradaHorario, hSalidaHorario);
                        if (horasProgramadas > minutosDescanso) {
                            horasProgramadas -= minutosDescanso;
                        }

                        if (totalMinutos > horasProgramadas) {
                            minutosTrabajados = horasProgramadas;
                            minutosExtras = totalMinutos - horasProgramadas;
                        } else {
                            minutosTrabajados = totalMinutos;
                            minutosExtras = 0;
                        }

                    } else {
                        long totalMinutos = ChronoUnit.MINUTES.between(entradaReal, salidaReal);

                        if (totalMinutos > 5 * 60 || tiempoAlmuerzoReal >= 0) {
                            totalMinutos -= descansoADescontar;
                        }
                        if (totalMinutos < 0)
                            totalMinutos = 0;

                        long jornadaBase = 8 * 60;
                        if (totalMinutos > jornadaBase) {
                            minutosTrabajados = jornadaBase;
                            minutosExtras = totalMinutos - jornadaBase;
                        } else {
                            minutosTrabajados = totalMinutos;
                            minutosExtras = 0;
                        }
                    }
                }
            }

            jornada.setMinutosTrabajados(minutosTrabajados);
            jornada.setMinutosExtras(minutosExtras);
            jornadaService.save(jornada);

        } catch (

        Exception e) {
            e.printStackTrace();
        }
    }
}