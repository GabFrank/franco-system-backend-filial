package com.franco.dev.graphql.impresion;

import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.service.empresarial.ImpresoraService;
import com.franco.dev.service.impresion.PrintRouterService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Endpoints de impresion con routing (Fase 3) en la filial.
 */
@Component
public class PrintRouterGraphQL implements GraphQLMutationResolver {

    @Autowired
    private PrintRouterService printRouterService;

    @Autowired
    private ImpresoraService impresoraService;

    public Boolean imprimirEnDestino(Long impresoraId, String payloadBase64) {
        byte[] payload = Base64.getDecoder().decode(payloadBase64);
        return printRouterService.imprimirLocalPorId(impresoraId, payload);
    }

    public Boolean imprimirEnImpresora(Long impresoraId, String payloadBase64) {
        byte[] payload = Base64.getDecoder().decode(payloadBase64);
        return printRouterService.imprimir(impresoraId, payload);
    }

    public Boolean imprimirPruebaEnImpresora(Long impresoraId) {
        Impresora impresora = impresoraService.findById(impresoraId).orElse(null);
        if (impresora == null) {
            return false;
        }
        byte[] payload = printRouterService.generarTicketPrueba(impresora);
        return printRouterService.imprimir(impresoraId, payload);
    }
}
