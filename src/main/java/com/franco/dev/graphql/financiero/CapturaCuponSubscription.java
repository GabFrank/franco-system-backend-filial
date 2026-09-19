package com.franco.dev.graphql.financiero;

import com.franco.dev.graphql.financiero.publisher.CapturaCuponPublisher;
import com.franco.dev.graphql.financiero.publisher.CapturaCuponUpdate;
import graphql.kickstart.tools.GraphQLSubscriptionResolver;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;

/**
 * La subscription de la captura, en su propia clase a proposito.
 *
 * <p><b>No puede vivir junto a la query y la mutation.</b> El pointcut de
 * {@code SecurityGraphQLAspect} es {@code target(GraphQLQueryResolver)}: matchea la <b>clase</b>,
 * no el metodo. Si la subscription estuviera en {@code CapturaCuponGraphQL} --que implementa los
 * tres resolvers-- heredaria el chequeo de login, y por WebSocket no hay {@code SecurityContext}:
 * el aspecto contesta "Sorry, you should log in first to do that!" y el desktop nunca se entera de
 * nada. Verificado el 2026-09-10 contra el filial real.
 *
 * <p>Es el mismo motivo por el que {@code sincEstado} y {@code deliverys} viven en la clase
 * {@code Subscription}, que implementa unicamente {@code GraphQLSubscriptionResolver}.
 *
 * <p>Lo que se emite es un timbre sin contenido; ver {@link CapturaCuponUpdate}.
 */
@Component
public class CapturaCuponSubscription implements GraphQLSubscriptionResolver {

    private final CapturaCuponPublisher publisher;

    public CapturaCuponSubscription(CapturaCuponPublisher publisher) {
        this.publisher = publisher;
    }

    public Publisher<CapturaCuponUpdate> capturaCuponSub() {
        return publisher.getPublisher();
    }
}
