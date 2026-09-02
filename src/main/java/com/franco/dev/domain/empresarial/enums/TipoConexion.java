package com.franco.dev.domain.empresarial.enums;

public enum TipoConexion {
    CUPS,
    USB,
    RED,
    /** Impresora compartida por un host Windows; el transporte lo hace el backend smb de CUPS. */
    SMB,
    BLUETOOTH
}
