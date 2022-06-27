package com.franco.dev.graphql.subscriptions;

import com.franco.dev.domain.configuracion.SincronizacionStatus;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.graphql.configuraciones.publisher.SincronizacionStatusPublisher;
import com.franco.dev.graphql.operaciones.publisher.DeliveryPublisher;
import com.franco.dev.graphql.operaciones.publisher.DeliveryUpdate;
import com.franco.dev.graphql.personas.input.PersonaUpdate;
import com.franco.dev.graphql.personas.publisher.PersonaPublisher;
import graphql.kickstart.tools.GraphQLSubscriptionResolver;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;

@Component
class Subscription implements GraphQLSubscriptionResolver {

//    private PersonaPublisher personaPublisher;

    private DeliveryPublisher deliveryPublisher;
    private SincronizacionStatusPublisher sincronizacionStatusPublisher;


//    Subscription(PersonaPublisher personaPublisher, DeliveryPublisher deliveryPublisher) {
//        this.personaPublisher = personaPublisher;
//        this.deliveryPublisher = deliveryPublisher;
//    }
//
//    Publisher<PersonaUpdate> personas() {
//        return personaPublisher.getPublisher();
//    }

    Publisher<Delivery> deliverys() {
        return deliveryPublisher.getPublisher();
    }
    Publisher<SincronizacionStatus> sincEstado() {
        return sincronizacionStatusPublisher.getPublisher();
    }

}
