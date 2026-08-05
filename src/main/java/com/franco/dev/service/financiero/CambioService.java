package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.repository.financiero.CambioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CambioService extends CrudService<Cambio, CambioRepository> {

    private final CambioRepository repository;

    @Override
    public CambioRepository getRepository() {
        return repository;
    }

    public Cambio findLastByMonedaId(Long id){
        return repository.findLastByCambioId(id);
    }

    /**
     * Devuelve el valor en guaraníes (Cambio.valorEnGs) de la última cotización
     * de la moneda, o {@code null} si la moneda no tiene ninguna cotización
     * registrada. Evita el NullPointerException de hacer
     * {@code findLastByMonedaId(id).getValorEnGs()} sobre un resultado nulo
     * (p.ej. cuando la cotización del día aún no fue cargada o no replicó a la
     * filial).
     */
    public Double findLastValorEnGsByMonedaId(Long id) {
        Cambio cambio = repository.findLastByCambioId(id);
        return cambio != null ? cambio.getValorEnGs() : null;
    }

    /**
     * Igual que {@link #findLastValorEnGsByMonedaId(Long)} pero garantiza un
     * divisor seguro (&gt; 0) para los cálculos internos de conversión
     * (subtotales del ticket). Si no hay cotización válida, cae a
     * {@code defaultValue} en lugar de lanzar excepción o dividir por cero.
     * No se usa para la cotización que muestra el POS (esa la resuelve
     * MonedaResolver.cambio y conserva el valor real).
     */
    public Double findLastValorEnGsByMonedaIdOrDefault(Long id, Double defaultValue) {
        Double valor = findLastValorEnGsByMonedaId(id);
        return (valor != null && valor > 0) ? valor : defaultValue;
    }

    @Override
    public Cambio save(Cambio entity) {
        Cambio e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}