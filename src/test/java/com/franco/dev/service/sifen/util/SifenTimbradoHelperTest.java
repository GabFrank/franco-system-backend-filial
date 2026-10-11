package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SifenTimbradoHelperTest {

    @Test
    void telefonoEmisor_elDelDetalleMandaYSeRecorta() {
        assertEquals("021123456", SifenTimbradoHelper.telefonoEmisor(detalle(" 021123456 ", "0982700027")));
    }

    @Test
    void telefonoEmisor_detalleVacioOEnBlancoUsaLaCabecera() {
        assertEquals("0982700027", SifenTimbradoHelper.telefonoEmisor(detalle(null, "0982700027")));
        assertEquals("0982700027", SifenTimbradoHelper.telefonoEmisor(detalle("", "0982700027")));
        assertEquals("0982700027", SifenTimbradoHelper.telefonoEmisor(detalle("   ", " 0982700027 ")));
    }

    @Test
    void telefonoEmisor_sinTelefonoEnNingunLadoEsNullNuncaVacio() {
        assertNull(SifenTimbradoHelper.telefonoEmisor(detalle(null, null)));
        assertNull(SifenTimbradoHelper.telefonoEmisor(detalle("", "  ")));
    }

    @Test
    void telefonoEmisor_sinDetalleOSinCabeceraEsNull() {
        assertNull(SifenTimbradoHelper.telefonoEmisor(null));
        TimbradoDetalle sinCabecera = new TimbradoDetalle();
        sinCabecera.setTelefono(" ");
        assertNull(SifenTimbradoHelper.telefonoEmisor(sinCabecera));
    }

    private static TimbradoDetalle detalle(String telefonoDetalle, String telefonoCabecera) {
        Timbrado timbrado = new Timbrado();
        timbrado.setTelefono(telefonoCabecera);
        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setTelefono(telefonoDetalle);
        detalle.setTimbrado(timbrado);
        return detalle;
    }
}
