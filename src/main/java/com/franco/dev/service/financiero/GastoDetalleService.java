package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.GastoDetalle;
import com.franco.dev.repository.financiero.GastoDetalleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class GastoDetalleService extends CrudService<GastoDetalle, GastoDetalleRepository> {

    private final GastoDetalleRepository repository;

    @Override
    public GastoDetalleRepository getRepository() {
        return repository;
    }



//    public List<GastoDetalle> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<GastoDetalle> findByGastoId(Long id){
        return repository.findByGastoId(id);
    }
}
