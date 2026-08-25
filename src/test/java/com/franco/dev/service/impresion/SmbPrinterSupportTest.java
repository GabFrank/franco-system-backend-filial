package com.franco.dev.service.impresion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.franco.dev.service.impresion.SmbPrinterSupport.deviceUri;
import static com.franco.dev.service.impresion.SmbPrinterSupport.recursosDeImpresora;
import static com.franco.dev.service.impresion.SmbPrinterSupport.redactar;
import static com.franco.dev.service.impresion.SmbPrinterSupport.redactarTexto;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El device-uri es lo unico que ve smbspool: si esta mal armado la cola queda instalada
 * pero nunca conecta (el sintoma que tuvimos en produccion con adm_ticket). No se puede
 * verificar contra un host Windows en CI, asi que estos tests fijan el formato.
 */
class SmbPrinterSupportTest {

    @Test
    void sinCredencialesQuedaLaUriDesnuda() {
        assertEquals("smb://100.64.0.10/adm_ticket",
                deviceUri("100.64.0.10", "adm_ticket", null, null, null));
    }

    @Test
    void usuarioYPasswordVanComoUserinfo() {
        assertEquals("smb://admin:secreta@100.64.0.10/adm_ticket",
                deviceUri("100.64.0.10", "adm_ticket", "admin", null, "secreta"));
    }

    @Test
    void usuarioSinPasswordNoDejaLosDosPuntos() {
        assertEquals("smb://admin@100.64.0.10/adm_ticket",
                deviceUri("100.64.0.10", "adm_ticket", "admin", null, null));
    }

    @Test
    void elDominioVaComoPrimerSegmentoDelPath() {
        // Formato de smbspool: smb://usuario:pass@workgroup/servidor/impresora
        assertEquals("smb://admin:secreta@WORKGROUP/100.64.0.10/adm_ticket",
                deviceUri("100.64.0.10", "adm_ticket", "admin", "WORKGROUP", "secreta"));
    }

    @Test
    void losCaracteresReservadosSeCodifican() {
        // Un password con @ o / partiria la URI en dos y CUPS conectaria a otro host.
        assertEquals("smb://adm%40bodega:p%40ss%2Fword%3A1@100.64.0.10/adm_ticket",
                deviceUri("100.64.0.10", "adm_ticket", "adm@bodega", null, "p@ss/word:1"));
    }

    @Test
    void elEspacioEnElRecursoSeCodifica() {
        assertEquals("smb://ADM-BODEGA/Microsoft%20Print%20to%20PDF",
                deviceUri("ADM-BODEGA", "Microsoft Print to PDF", null, null, null));
    }

    @Test
    void hostVacioEsError() {
        assertThrows(IllegalArgumentException.class,
                () -> deviceUri("  ", "adm_ticket", null, null, null));
    }

    @Test
    void recursoVacioEsError() {
        assertThrows(IllegalArgumentException.class,
                () -> deviceUri("100.64.0.10", null, null, null, null));
    }

    @Test
    void soloDevuelveLosSharesDeImpresora() {
        String salida = String.join("\n",
                "Anonymous login successful",
                "Disk|ADMIN$|Remote Admin",
                "Disk|C$|Default share",
                "Printer|adm_ticket|Impresora de tickets ADM",
                "IPC|IPC$|Remote IPC",
                "Printer|Microsoft Print to PDF|",
                "Server|ADM-BODEGA|",
                "Workgroup|WORKGROUP|ADM-BODEGA");

        List<DispositivoDetectado> recursos = recursosDeImpresora("100.64.0.10", salida);

        assertEquals(2, recursos.size());
        assertEquals("adm_ticket", recursos.get(0).getNombre());
        assertEquals("Impresora de tickets ADM", recursos.get(0).getDescripcion());
        assertEquals("Microsoft Print to PDF", recursos.get(1).getNombre());
    }

    @Test
    void laUriDelRecursoDetectadoNoLlevaCredenciales() {
        // Esta lista viaja al frontend: no puede filtrar la contrasena del share.
        List<DispositivoDetectado> recursos =
                recursosDeImpresora("100.64.0.10", "Printer|adm_ticket|Tickets");

        assertEquals("smb://100.64.0.10/adm_ticket", recursos.get(0).getUri());
        assertEquals("smb", recursos.get(0).getClase());
    }

    @Test
    void salidaSinSharesDevuelveListaVacia() {
        String salida = "session setup failed: NT_STATUS_ACCESS_DENIED";
        assertTrue(recursosDeImpresora("100.64.0.10", salida).isEmpty());
    }

    @Test
    void elLogNoPuedeFiltrarLaPasswordDelShare() {
        // CupsAdminService loguea el comando completo; con la URI cruda la contrasena de
        // Windows terminaria en backend*.log.
        List<String> cmd = List.of("lpadmin", "-p", "adm_ticket", "-E",
                "-v", "smb://admin:secreta@100.64.0.10/adm_ticket", "-m", "raw");

        assertEquals(List.of("lpadmin", "-p", "adm_ticket", "-E",
                "-v", "smb://admin:***@100.64.0.10/adm_ticket", "-m", "raw"), redactar(cmd));
    }

    @Test
    void tambienRedactaLaPasswordDeSmbclient() {
        assertEquals(List.of("smbclient", "-L", "//100.64.0.10", "-g", "-U", "WORKGROUP\\admin%***"),
                redactar(List.of("smbclient", "-L", "//100.64.0.10", "-g", "-U", "WORKGROUP\\admin%secreta")));
    }

    @Test
    void unaUriSinPasswordQuedaIgual() {
        List<String> cmd = List.of("lpadmin", "-v", "smb://100.64.0.10/adm_ticket");
        assertEquals(cmd, redactar(cmd));
    }

    @Test
    void tambienRedactaLaSalidaDelComando() {
        // lpadmin repite el device-uri en sus mensajes de error, y esa salida tambien se loguea.
        assertEquals("lpadmin: Bad device-uri \"smb://admin:***@100.64.0.10/adm_ticket\"",
                redactarTexto("lpadmin: Bad device-uri \"smb://admin:secreta@100.64.0.10/adm_ticket\""));
    }

    @Test
    void unaSalidaSinCredencialesQuedaIgual() {
        assertEquals("Unable to connect to CIFS host after (tried 3 times)",
                redactarTexto("Unable to connect to CIFS host after (tried 3 times)"));
        assertNull(redactarTexto(null));
    }
}
