package com.franco.dev.utilitarios.print;

/**
 * Códigos QR de los tickets de retiro y de cierre de caja del PDV.
 *
 * Los lee el lector de códigos de la caja mayor (desktop, {@code QrLectorService}), que parsea por
 * posición el formato de la transferencia: {@code frc-{suc}-{TIPO}-{id}-{suc}-{componente}-{data}-{ts}}.
 * La sucursal va dos veces porque los ids de retiro y de caja se repiten entre sucursales
 * (PK compuesta en el central).
 *
 * Devuelve null cuando falta un dato: un código sin sucursal traería el documento equivocado.
 */
public final class QrTicket {

    private static final String COMPONENTE = "IngresarRetiroCajaMayor";

    private QrTicket() {
    }

    /** {@code frc-{suc}-RETIRO-{retiroId}-{suc}-IngresarRetiroCajaMayor-{cajaId}-null} */
    public static String retiro(Long sucursalId, Long retiroId, Long cajaId) {
        if (sucursalId == null || retiroId == null) return null;
        return "frc-" + sucursalId + "-RETIRO-" + retiroId + "-" + sucursalId + "-" + COMPONENTE + "-" + cajaId + "-null";
    }

    /** {@code frc-{suc}-PDVCAJA-{cajaId}-{suc}-IngresarRetiroCajaMayor-null-null} */
    public static String caja(Long sucursalId, Long cajaId) {
        if (sucursalId == null || cajaId == null) return null;
        return "frc-" + sucursalId + "-PDVCAJA-" + cajaId + "-" + sucursalId + "-" + COMPONENTE + "-null-null";
    }
}
