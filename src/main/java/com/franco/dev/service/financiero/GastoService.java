package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Service
@AllArgsConstructor
public class GastoService extends CrudService<Gasto, GastoRepository> {

    private final GastoRepository repository;

    @Override
    public GastoRepository getRepository() {
        return repository;
    }

//    public List<Gasto> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<Gasto> findByDate(String inicio, String fin) {
        return repository.findByCreadoEnBetween(toDate(inicio), toDate(fin));
    }

    public List<Gasto> findByCajaId(Long id) {
        return repository.findByCajaId(id);
    }

    @Override
    public Gasto save(Gasto entity) {
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        if(entity.getObservacion() != null) entity.setObservacion(entity.getObservacion().toUpperCase());
        return super.save(entity);
    }

    @Override
    public Gasto saveAndSend(Gasto entity, Boolean recibir) {
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        // El control de efectivo del alta va en GastoGraphQL (issue #80): aca armaria un ciclo de constructores con PdvCajaService.
        // Ya no se escribe movimiento_caja: el saldo sale de generarBalance (#80).
        return super.save(entity);
    }
}