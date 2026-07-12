package com.franco.dev.graphql.impresion;

import com.franco.dev.service.impresion.CupsAdminService;
import com.franco.dev.service.impresion.DispositivoDetectado;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detectar-para-instalar impresoras a nivel CUPS (Linux) en esta filial.
 */
@Component
public class CupsAdminGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CupsAdminService cupsAdminService;

    /** Dispositivos conectables (USB/red/serial) aunque no tengan cola creada. */
    public List<DispositivoDetectado> dispositivosParaInstalar() {
        return cupsAdminService.detectarDispositivos();
    }

    /** Instala una cola CUPS (raw por defecto, ideal para termicas ESC/POS). */
    public Boolean instalarImpresoraCups(String nombreCola, String uri, Boolean raw) {
        return cupsAdminService.instalarImpresora(nombreCola, uri, raw == null || raw);
    }
}
