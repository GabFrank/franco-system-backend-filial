package com.franco.dev.graphql.financiero;

import com.franco.dev.service.sifen.service.SifenService;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** #134: el escritorio muestra el pais del cliente solo si el filial factura electronico (la regla del builder). */
class FacturaElectronicaHabilitadaTest {

    private FacturaLegalGraphQL resolver(SifenService sifen) {
        FacturaLegalGraphQL g = new ObjenesisStd().newInstance(FacturaLegalGraphQL.class);
        ReflectionTestUtils.setField(g, "sifenService", sifen);
        return g;
    }

    @Test
    void sinSifen_noEsElectronica() {
        assertFalse(resolver(null).facturaElectronicaHabilitada());
        SifenService apagado = mock(SifenService.class);
        when(apagado.isSifenEnabled()).thenReturn(false);
        assertFalse(resolver(apagado).facturaElectronicaHabilitada());
    }

    @Test
    void conSifenHabilitado_esElectronica() {
        SifenService prendido = mock(SifenService.class);
        when(prendido.isSifenEnabled()).thenReturn(true);
        assertTrue(resolver(prendido).facturaElectronicaHabilitada());
    }
}
