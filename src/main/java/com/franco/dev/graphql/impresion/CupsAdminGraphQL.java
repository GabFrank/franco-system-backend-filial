package com.franco.dev.graphql.impresion;

import com.franco.dev.service.impresion.ColaEstado;
import com.franco.dev.service.impresion.CupsAdminService;
import com.franco.dev.service.impresion.DispositivoDetectado;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detectar-para-instalar impresoras a nivel CUPS (Linux) en este host, y ver/reparar el
 * estado real de las colas.
 */
@Component
public class CupsAdminGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CupsAdminService cupsAdminService;

    /** Dispositivos conectables (USB/red/serial) aunque no tengan cola creada. */
    public List<DispositivoDetectado> dispositivosParaInstalar() {
        return cupsAdminService.detectarDispositivos();
    }

    /**
     * Shares de impresora publicados por un host Windows. La password autentica la consulta y
     * se descarta; las URIs devueltas van sin credenciales.
     */
    public List<DispositivoDetectado> recursosSmb(String host, String usuario, String dominio, String password) {
        return cupsAdminService.detectarRecursosSmb(host, usuario, dominio, password);
    }

    /** Estado real de las colas de este host (lpstat -p), no el flag activo de la BD. */
    public List<ColaEstado> estadoColas() {
        return cupsAdminService.estadoColas();
    }

    /** Instala una cola CUPS (raw por defecto, ideal para termicas ESC/POS). */
    public Boolean instalarImpresoraCups(String nombreCola, String uri, Boolean raw) {
        return cupsAdminService.instalarImpresora(nombreCola, uri, raw == null || raw);
    }

    /**
     * Instala una cola CUPS que entrega los jobs a una impresora compartida por un host Windows
     * (backend smb de CUPS / smbspool). La password solo se usa para armar el device-uri.
     */
    public Boolean instalarImpresoraSmb(String nombreCola, String host, String recurso,
                                        String usuario, String dominio, String password, Boolean raw) {
        return cupsAdminService.instalarImpresoraSmb(nombreCola, host, recurso, usuario, dominio,
                password, raw == null || raw);
    }

    /**
     * Comparte una cola CUPS ya instalada en este host (filial o central) via IPP, para que otro
     * host pueda instalar una cola proxy que reenvie hacia ella (ver {@code instalarImpresoraCups}).
     */
    public Boolean compartirColaCups(String nombreCola, String ipDestino) {
        return cupsAdminService.compartirCola(nombreCola, ipDestino);
    }

    /** Rehabilita una cola frenada por CUPS y libera los jobs retenidos. */
    public Boolean reactivarCola(String nombreCola) {
        return cupsAdminService.reactivarCola(nombreCola);
    }
}
