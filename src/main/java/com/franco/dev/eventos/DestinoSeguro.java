package com.franco.dev.eventos;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/**
 * Control de SSRF antes de cada envio: solo https, host en la lista permitida (si hay) y ninguna IP
 * resuelta en rangos privados, de loopback, link-local o CGNAT (100.64/10, la red de Tailscale).
 */
public final class DestinoSeguro {

    private DestinoSeguro() {
    }

    /** null si se puede enviar; si no, el motivo. */
    public static String validar(String url, List<String> hostsPermitidos) {
        URI u;
        try {
            u = URI.create(url);
        } catch (Exception e) {
            return "URL invalida";
        }
        if (!"https".equalsIgnoreCase(u.getScheme())) return "Solo se acepta https";
        String host = u.getHost();
        if (host == null) return "URL sin host";
        if (hostsPermitidos != null && !hostsPermitidos.isEmpty()
                && hostsPermitidos.stream().noneMatch(h -> h.equalsIgnoreCase(host))) {
            return "Host fuera de frc.webhooks.hosts-permitidos";
        }
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (bloqueada(a)) return "El host resuelve a una IP interna";
            }
        } catch (Exception e) {
            return "No resuelve el host";
        }
        return null;
    }

    static boolean bloqueada(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            // fc00::/7 (ULA) e IPv4 mapeada (::ffff:a.b.c.d)
            if ((b[0] & 0xfe) == 0xfc) return true;
            boolean mapeada = true;
            for (int i = 0; i < 10; i++) if (b[i] != 0) mapeada = false;
            if (mapeada && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return bloqueada(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (Exception e) {
                    return true;
                }
            }
            return false;
        }
        if (a instanceof Inet4Address) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return true; // 100.64/10
            if (b0 == 0) return true;
        }
        return false;
    }
}
