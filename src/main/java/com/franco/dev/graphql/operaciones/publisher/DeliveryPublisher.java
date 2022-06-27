package com.franco.dev.graphql.operaciones.publisher;

import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.operaciones.input.DeliveryInput;
import com.franco.dev.graphql.personas.input.PersonaUpdate;
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
public class DeliveryPublisher {

    private final Flowable<Delivery> publisher;

    private ObservableEmitter<Delivery> emitter;

    public DeliveryPublisher() {
        Observable<Delivery> observable = Observable.create(emitter -> {
            this.emitter = emitter;
        });

        observable.share();
        ConnectableObservable<Delivery> connectableObservable = observable.publish();
        connectableObservable.connect();


        publisher = connectableObservable.toFlowable(BackpressureStrategy.BUFFER);
    }

    public void publish(final Delivery entity) {
        ModelMapper m = new ModelMapper();
        Delivery entityUpdate = m.map(entity, Delivery.class);
        emitter.onNext(entityUpdate);
    }


    public Flowable<Delivery> getPublisher() {
        return publisher;
    }


}
