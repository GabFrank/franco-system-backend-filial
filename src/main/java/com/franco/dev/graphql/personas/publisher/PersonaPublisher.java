package com.franco.dev.graphql.personas.publisher;

import com.franco.dev.domain.personas.Persona;
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
public class PersonaPublisher {

    private final Flowable<PersonaUpdate> publisher;

    private ObservableEmitter<PersonaUpdate> emitter;

    public PersonaPublisher () {
        Observable<PersonaUpdate> personaObservable = Observable.create(emitter -> {
            this.emitter = emitter;
        });

        personaObservable.share();
        ConnectableObservable<PersonaUpdate> connectableObservable = personaObservable.publish();
        connectableObservable.connect();


        publisher = connectableObservable.toFlowable(BackpressureStrategy.BUFFER);
    }

    public void publish(final Persona persona) {
        ModelMapper m = new ModelMapper();
        PersonaUpdate personaUpdate = m.map(persona, PersonaUpdate.class);
        emitter.onNext(personaUpdate);
    }


    public Flowable<PersonaUpdate> getPublisher() {
        return publisher;
    }


}
