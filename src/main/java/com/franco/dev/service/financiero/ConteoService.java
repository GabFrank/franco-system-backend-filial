package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.ConteoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ConteoService extends CrudService<Conteo, ConteoRepository> {

    private final ConteoRepository repository;

    @Override
    public ConteoRepository getRepository() {
        return repository;
    }


//    public List<Conteo> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public Conteo findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    public Double getTotalPorMoneda(Long conteoId, Long monedaId) {
        return repository.getTotalPorMoneda(conteoId, monedaId);
    }

    @Override
    public Conteo save(Conteo entity) {
        Conteo e = super.save(entity);
        return e;
    }

    @Override
    public Conteo saveAndSend(Conteo entity, Boolean recibir) {
        Conteo e = super.save(entity);
        propagacionService.propagarEntidad(e, TipoEntidad.CONTEO, recibir);
        return e;
    }
}