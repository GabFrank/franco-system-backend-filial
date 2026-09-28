package com.franco.dev.service.financiero;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Corre la purga de fotos de cupon una vez por dia.
 *
 * <p><b>Apagado por default</b> ({@code matchIfMissing = false}), como todos los schedulers de este
 * modulo. Borrar archivos del disco no es reversible, y el disco es el mismo donde Postgres escribe
 * el WAL: no es algo que deba prenderse solo porque alguien despliega una version.
 *
 * <p><b>Y arranca en simulacion.</b> {@code frc.purga-imagenes.simulacion} viene en {@code true}:
 * la primera vez que se prende en un filial, la corrida <b>loguea lo que borraria</b> sin tocar
 * nada. Recien cuando alguien miro ese log y le cerro el numero, se pasa a false. Es barato y
 * evita el unico error que no tiene vuelta atras.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "frc.purga-imagenes.enabled", havingValue = "true", matchIfMissing = false)
public class PurgaImagenesCuponScheduler {

    private final PurgaImagenesCuponService service;
    private final boolean simulacion;

    public PurgaImagenesCuponScheduler(PurgaImagenesCuponService service,
                                       @Value("${frc.purga-imagenes.simulacion:true}") boolean simulacion) {
        this.service = service;
        this.simulacion = simulacion;
        log.info("purga de imagenes de cupon activa, simulacion={}", simulacion);
    }

    /**
     * De madrugada, cuando la caja esta cerrada.
     *
     * <p>{@code initialDelay} alto a proposito: si el filial se reinicia varias veces seguidas
     * --un despliegue con rollback, por ejemplo-- no conviene que cada arranque dispare una purga.
     */
    @Scheduled(initialDelayString = "${frc.purga-imagenes.initial-delay:600000}",
               fixedDelayString = "${frc.purga-imagenes.fixed-delay:86400000}")
    public void correr() {
        try {
            service.purgar(simulacion);
        } catch (Throwable e) {
            // Throwable y no Exception, por la misma razon que el resto del modulo: en este repo
            // ya hubo un arranque caido por un Error que un catch (Exception) dejo pasar. Y una
            // purga que falla no puede llevarse puesto el scheduler de la sucursal.
            log.error("fallo la purga de imagenes de cupon", e);
        }
    }
}
