package com.franco.dev.graphql.financiero.publisher;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Canal por el que el resultado de la captura llega a los desktops conectados.
 *
 * <p>Mismo armado que {@code TransferenciaQrEscaneadoPublisher} del central: observable
 * caliente, sin persistencia. Un desktop que se suscribe despues del aviso no lo recibe.
 *
 * <p><b>Por eso la subscription no alcanza sola.</b> Si el desktop se reinicia, pierde la red un
 * segundo o el aviso sale antes de que se suscriba, la captura queda LISTA en la base y nadie se
 * entera. La query {@code capturaCupon(token)} es la red de contencion: el desktop consulta al
 * suscribirse y cada tantos segundos mientras espera. Ver el caso 4 de §2.10 de
 * FASE-2-TICKET-FISICO.md.
 */
@Slf4j
@Component
public class CapturaCuponPublisher {

    private final Flowable<CapturaCuponUpdate> publisher;

    private ObservableEmitter<CapturaCuponUpdate> emitter;

    public CapturaCuponPublisher() {
        Observable<CapturaCuponUpdate> observable = Observable.create(emitter -> {
            this.emitter = emitter;
        });

        ConnectableObservable<CapturaCuponUpdate> connectableObservable = observable.publish();
        connectableObservable.connect();

        publisher = connectableObservable.toFlowable(BackpressureStrategy.BUFFER);
    }

    public void publish(final CapturaCuponUpdate entity) {
        if (emitter == null) {
            // No deberia pasar: connect() en el constructor deja el emitter listo desde el
            // arranque. Si pasa, el desktop no se entera por subscription y solo lo ve cuando
            // sondea --y eso, sin este aviso, es indistinguible de un OCR lento.
            log.warn("captura {} lista pero el canal de avisos no esta armado", entity.getToken());
            return;
        }
        log.info("aviso de captura {} -> {}", entity.getToken(), entity.getEstado());
        emitter.onNext(entity);
    }

    public Flowable<CapturaCuponUpdate> getPublisher() {
        return publisher;
    }
}
