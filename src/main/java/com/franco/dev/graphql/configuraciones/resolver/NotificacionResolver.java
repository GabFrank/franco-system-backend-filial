package com.franco.dev.graphql.configuraciones.resolver;

import com.franco.dev.domain.configuracion.Notificacion;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class NotificacionResolver implements GraphQLResolver<Notificacion> {

    public Integer conteoComentarios(Notificacion notificacion) {
        return 0;
    }
}
