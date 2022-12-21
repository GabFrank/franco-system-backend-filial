package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoPersonas;
import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.MovimientoPersonasRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MovimientoPersonasService extends CrudService<MovimientoPersonas, MovimientoPersonasRepository> {

    private final MovimientoPersonasRepository repository;

    @Override
    public MovimientoPersonasRepository getRepository() {
        return repository;
    }

//    public List<MovimientoPersonas> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<MovimientoPersonas> findByPersonaAndVencimiento(Long id, LocalDateTime inicio, LocalDateTime fin, Boolean activo) {
        if (activo == null) {
            return repository.findAllByPersonaIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByIdAsc(id, inicio, fin);
        } else {
            return repository.findAllByPersonaIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualAndActivoOrderByIdAsc(id, inicio, fin, activo);
        }
    }

    @Override
    public MovimientoPersonas save(MovimientoPersonas entity) {
        MovimientoPersonas e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}