package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.roshka.sifen.core.fields.request.de.TgEmis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * jsifenlib escribe {@code dTelEmi} siempre: con el telefono del detalle vacio salia el elemento
 * vacio y SIFEN rechazaba el lote con 0160 (issue #139). El emisor usa el de la cabecera.
 */
class SifenEmisorTelefonoTest {

    private final SifenService service =
            new SifenService(null, null, null, null, null, null, null, null, null);

    @Test
    void detalleSinTelefono_usaElDeLaCabecera() {
        TgEmis gEmis = service.construirDatosEmisor(factura(null, "0982700027"));
        assertEquals("0982700027", gEmis.getdTelEmi());
    }

    @Test
    void detalleEnBlanco_usaElDeLaCabecera() {
        TgEmis gEmis = service.construirDatosEmisor(factura("   ", "0982700027"));
        assertEquals("0982700027", gEmis.getdTelEmi());
    }

    @Test
    void detalleConTelefono_mandaElDetalle() {
        TgEmis gEmis = service.construirDatosEmisor(factura("021123456", "0982700027"));
        assertEquals("021123456", gEmis.getdTelEmi());
    }

    private static FacturaLegal factura(String telefonoDetalle, String telefonoCabecera) {
        Timbrado timbrado = new Timbrado();
        timbrado.setRuc("80012345-6");
        timbrado.setTelefono(telefonoCabecera);
        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setTimbrado(timbrado);
        detalle.setTelefono(telefonoDetalle);
        detalle.setCodigoCiudad("1");
        FacturaLegal factura = new FacturaLegal();
        factura.setTimbradoDetalle(detalle);
        return factura;
    }
}
