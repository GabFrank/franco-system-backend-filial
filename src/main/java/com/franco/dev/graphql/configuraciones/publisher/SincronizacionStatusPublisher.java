package com.franco.dev.graphql.configuraciones.publisher;

import com.franco.dev.domain.configuracion.SincronizacionStatus;
import com.franco.dev.domain.operaciones.Delivery;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SincronizacionStatusPublisher {

    private final Flowable<SincronizacionStatus> publisher;

    private ObservableEmitter<SincronizacionStatus> emitter;

    public SincronizacionStatusPublisher() {
        Observable<SincronizacionStatus> observable = Observable.create(emitter -> {
            this.emitter = emitter;
        });

        observable.share();
        ConnectableObservable<SincronizacionStatus> connectableObservable = observable.publish();
        connectableObservable.connect();

        publisher = connectableObservable.toFlowable(BackpressureStrategy.BUFFER);
    }

    public void publish(final SincronizacionStatus entity) {
        emitter.onNext(entity);
    }


    public Flowable<SincronizacionStatus> getPublisher() {
        return publisher;
    }


}
