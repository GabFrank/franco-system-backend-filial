package com.franco.dev.service.financiero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La direccion que va dentro del QR de captura.
 *
 * <p>Una maquina de filial tiene varias IPv4 al mismo tiempo y solo una le sirve al telefono que
 * va a sacar la foto: la del wifi del local. Elegir mal no da error en ningun lado --el QR se
 * dibuja igual-- y el sintoma aparece recien cuando el cajero escanea y el telefono no carga
 * nada. Por eso el criterio va probado: no se puede ejercitar pidiendole a la maquina de turno
 * que tenga justo las interfaces del caso.
 */
class CapturaCuponIpLanTest {

    // ---- interfaces ----

    @Test
    void aceptaLasInterfacesFisicasHabituales() {
        assertTrue(CapturaCuponService.interfazUtil("eth0"));
        assertTrue(CapturaCuponService.interfazUtil("enp3s0"));
        assertTrue(CapturaCuponService.interfazUtil("wlan0"));
        assertTrue(CapturaCuponService.interfazUtil("en0"));          // macOS
        assertTrue(CapturaCuponService.interfazUtil("Ethernet 2"));   // Windows
    }

    @Test
    void descartaLaVpnCualquieraSeaLaGeneracion() {
        assertTrue(CapturaCuponService.interfazUtil("eth0"));
        assertFalse(CapturaCuponService.interfazUtil("tailscale0"));
        assertFalse(CapturaCuponService.interfazUtil("ztppmkrvsu"));   // ZeroTier, filiales viejas
    }

    @Test
    void descartaLasRedesDeContenedores() {
        assertFalse(CapturaCuponService.interfazUtil("docker0"));
        assertFalse(CapturaCuponService.interfazUtil("br-1a2b3c4d"));
        assertFalse(CapturaCuponService.interfazUtil("veth9f8e7d"));
        assertFalse(CapturaCuponService.interfazUtil("virbr0"));
    }

    @Test
    void noSeLeEscapaPorMayusculas() {
        // En Windows los nombres vienen con mayusculas y no siempre como uno espera.
        assertFalse(CapturaCuponService.interfazUtil("Tailscale0"));
        assertFalse(CapturaCuponService.interfazUtil("Docker0"));
    }

    @Test
    void nombreNuloNoRevienta() {
        assertFalse(CapturaCuponService.interfazUtil(null));
    }

    // ---- direcciones ----

    @Test
    void aceptaLosTresRangosPrivados() {
        assertTrue(CapturaCuponService.direccionUtil("192.168.0.15", true));
        assertTrue(CapturaCuponService.direccionUtil("10.0.0.8", true));
        assertTrue(CapturaCuponService.direccionUtil("172.25.3.4", true));   // la LAN real de las filiales
    }

    @Test
    void rechazaLaPublica() {
        // isSiteLocalAddress false: no la va a alcanzar el telefono, y no queremos publicar la
        // pagina hacia afuera aunque la alcanzara.
        assertFalse(CapturaCuponService.direccionUtil("159.203.86.103", false));
    }

    @Test
    void rechazaTailscaleAunqueVengaComoPrivada() {
        // 100.64/10 no es site-local para Java, pero se chequea igual: si algun dia una JVM lo
        // considerara privado, el QR apuntaria a una direccion que el telefono no alcanza.
        assertFalse(CapturaCuponService.direccionUtil("100.64.0.2", true));    // mauro
        assertFalse(CapturaCuponService.direccionUtil("100.64.0.11", true));   // filial 1
        assertFalse(CapturaCuponService.direccionUtil("100.127.255.254", true));
    }

    @Test
    void cientoPuntoAlgoQueNoEsCgnatSiSirve() {
        // El rango es 100.64-100.127. 100.0.x y 100.128.x son otra cosa; si son site-local para
        // la JVM, no hay motivo para descartarlas.
        assertTrue(CapturaCuponService.direccionUtil("100.63.0.1", true));
        assertTrue(CapturaCuponService.direccionUtil("100.128.0.1", true));
    }

    @Test
    void rechazaLaDeDocker() {
        // 172.17/16 SI es site-local, asi que sin nombrarla se colaria como si fuera la LAN.
        assertFalse(CapturaCuponService.direccionUtil("172.17.0.1", true));
    }

    @Test
    void elRestoDelBloque172SiSirve() {
        // El bloque privado va de 172.16 a 172.31: solo el .17 es de docker. Las filiales estan
        // justamente en 172.25.
        assertTrue(CapturaCuponService.direccionUtil("172.16.0.1", true));
        assertTrue(CapturaCuponService.direccionUtil("172.18.0.1", true));
        assertTrue(CapturaCuponService.direccionUtil("172.31.255.254", true));
    }

    @Test
    void direccionNulaNoRevienta() {
        assertFalse(CapturaCuponService.direccionUtil(null, true));
    }
}
