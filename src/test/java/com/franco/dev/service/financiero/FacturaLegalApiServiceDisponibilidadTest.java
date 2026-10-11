package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.dto.factura.DisponibilidadTimbradoDetalleResponseDTO;
import com.franco.dev.service.sifen.service.SifenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La API crea el DE por su cuenta. Si el timbrado no tiene telefono, la disponibilidad lo dice antes
 * de crear la factura: crearFacturaLegal responde 400 con el motivo en vez de 500 (issue #139).
 */
class FacturaLegalApiServiceDisponibilidadTest {

    private TimbradoDetalleService timbradoDetalleService;
    private SifenService sifenService;
    private FacturaLegalApiService service;
    private TimbradoDetalle detalle;

    @BeforeEach
    void setUp() {
        timbradoDetalleService = mock(TimbradoDetalleService.class);
        sifenService = mock(SifenService.class);
        service = new FacturaLegalApiService(timbradoDetalleService, null, null, null, null, sifenService,
                null, null, null, null, null);

        Timbrado timbrado = new Timbrado();
        timbrado.setActivo(true);
        timbrado.setIsElectronico(true);
        timbrado.setNumero("12345678");
        detalle = new TimbradoDetalle();
        detalle.setId(9L);
        detalle.setActivo(true);
        detalle.setTimbrado(timbrado);
        detalle.setNumeroActual(10L);
        detalle.setRangoHasta(1000L);

        when(timbradoDetalleService.findById(9L)).thenReturn(Optional.of(detalle));
        when(sifenService.isSifenEnabled()).thenReturn(true);
    }

    @Test
    void electronicoSinTelefonoEnNingunLado_noEstaDisponible() {
        detalle.setTelefono(" ");

        DisponibilidadTimbradoDetalleResponseDTO r = service.verificarDisponibilidadTimbradoDetalle(9L);

        assertFalse(r.getDisponible());
        assertTrue(r.getMensaje().startsWith("TIMBRADO_SIN_TELEFONO"), r.getMensaje());
    }

    @Test
    void electronicoConTelefonoSoloEnLaCabecera_estaDisponible() {
        detalle.getTimbrado().setTelefono("0982700027");

        assertTrue(service.verificarDisponibilidadTimbradoDetalle(9L).getDisponible());
    }

    @Test
    void papelSinTelefono_estaDisponible() {
        detalle.getTimbrado().setIsElectronico(false);
        when(sifenService.isSifenEnabled()).thenReturn(false);

        assertTrue(service.verificarDisponibilidadTimbradoDetalle(9L).getDisponible());
    }
}
