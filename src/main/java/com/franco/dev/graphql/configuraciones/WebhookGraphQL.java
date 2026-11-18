package com.franco.dev.graphql.configuraciones;

import com.franco.dev.eventos.EventosRepositorio;
import com.franco.dev.eventos.EventoEmisor;
import com.franco.dev.service.seguridad.RolSesionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Webhooks en la filial: solo el envio de prueba. Las suscripciones se editan en el central. */
@Component
public class WebhookGraphQL implements GraphQLMutationResolver {

    @Autowired
    private RolSesionService rolSesionService;

    @Autowired
    private EventosRepositorio eventosRepositorio;

    @Autowired
    private EventoEmisor emisor;

    public Boolean webhookEnviarPrueba(Long suscripcionId) {
        rolSesionService.exigirRol("WEBHOOKS GESTIONAR");
        if (!emisor.encendido()) return false;
        return eventosRepositorio.crearPrueba(suscripcionId, emisor.eventoIdNuevo(), emisor.getSucursalId());
    }
}
