package com.franco.dev.service.seguridad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Para tests que mockean el auditor: un mock de Mockito devolveria 0L como usuario que firma. */
public final class AuditorUsuarioIdMocks {

    private AuditorUsuarioIdMocks() {
    }

    /** Como el auditor real con el interruptor apagado (el default): firma el input. */
    public static AuditorUsuarioId firmarElInput(AuditorUsuarioId auditor) {
        when(auditor.firmante(any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(auditor.firmanteSecundario(any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        return auditor;
    }

    public static AuditorUsuarioId queFirmaElInput() {
        return firmarElInput(mock(AuditorUsuarioId.class));
    }
}
