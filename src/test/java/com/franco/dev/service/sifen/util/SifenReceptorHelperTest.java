package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.general.Pais;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.service.sifen.util.SifenReceptorHelper.ConfiguracionReceptor;
import com.franco.dev.service.sifen.util.SifenReceptorHelper.ReceptorSinPaisException;
import com.roshka.sifen.core.types.PaisType;
import com.roshka.sifen.core.types.TiNatRec;
import com.roshka.sifen.core.types.TiTiOpe;
import com.roshka.sifen.core.types.TiTipDocRec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Receptor extranjero (#134). Antes todo documento que no fueran 6-8 digitos salia como "Otro" y SIFEN
 * lo rechazaba (0160); los extranjeros con documento de 6-8 digitos se declaraban cedula paraguaya.
 */
class SifenReceptorHelperTest {

    private static Pais pais(String codigo, String descripcion) {
        Pais p = new Pais();
        p.setCodigo(codigo);
        p.setDescripcion(descripcion);
        return p;
    }

    private static Cliente cliente(String nombre, String documento, Pais pais, Boolean tributa) {
        Persona persona = new Persona();
        persona.setNombre(nombre);
        persona.setDocumento(documento);
        persona.setPais(pais);
        Cliente cliente = new Cliente();
        cliente.setPersona(persona);
        cliente.setTributa(tributa);
        return cliente;
    }

    private static ConfiguracionReceptor config(Cliente cliente) {
        return SifenReceptorHelper.determinarConfiguracionReceptor(cliente, 100_000.0);
    }

    // ---- extranjero: B2F con su pais ----

    @Test
    void brasilenoConCpf_esB2fConCedulaExtranjera() {
        ConfiguracionReceptor c = config(cliente("joao silva", "123.456.789-01", pais("BR", "Brasil"), false));

        assertEquals(TiNatRec.NO_CONTRIBUYENTE, c.iNatRec);
        assertEquals(TiTiOpe.B2F, c.iTiOpe);
        assertEquals(PaisType.BRA, c.cPaisRec);
        assertEquals(TiTipDocRec.CEDULA_EXTRANJERA, c.iTipIDRec);
        assertEquals("12345678901", c.dNumIDRec);
        assertEquals("JOAO SILVA", c.dNomRec);
        assertEquals("Brasil", c.dDirRec, "sin direccion, el nombre del pais (dDirRec es obligatorio en B2F)");
    }

    @Test
    void rgDeSieteDigitosConTributa_noPasaPorContribuyente() {
        // La factura manual crea el cliente con tributa=true: sin pais, este RG seria un RUC paraguayo.
        ConfiguracionReceptor c = config(cliente("JOAO", "1234567", pais("BR", "Brasil"), true));

        assertEquals(TiTiOpe.B2F, c.iTiOpe);
        assertEquals(TiTipDocRec.CEDULA_EXTRANJERA, c.iTipIDRec);
        assertNull(c.dRucRec);
    }

    @Test
    void documentoConLetras_esPasaporte() {
        ConfiguracionReceptor c = config(cliente("JOAO", "FX123456", pais("BR", "Brasil"), false));

        assertEquals(TiTipDocRec.PASAPORTE, c.iTipIDRec);
        assertEquals("FX123456", c.dNumIDRec);
    }

    @Test
    void conDireccion_laUsaRecortada() {
        Cliente cliente = cliente("JOAO", "12345678901", pais("BR", "Brasil"), false);
        cliente.getPersona().setDireccion("  RUA DAS FLORES 10  ");

        assertEquals("RUA DAS FLORES 10", config(cliente).dDirRec);
    }

    @Test
    void direccionEnBlanco_usaElPais() {
        Cliente cliente = cliente("JOAO", "12345678901", pais("BR", "Brasil"), false);
        cliente.getPersona().setDireccion("   ");

        assertEquals("Brasil", config(cliente).dDirRec);
    }

    @Test
    void codigosDeLaTablaDePaises() {
        assertEquals(PaisType.ARG, config(cliente("A", "12345678", pais("ARG", "Argentina"), false)).cPaisRec);
        assertEquals(PaisType.USA, config(cliente("A", "12345678", pais("US", "ESTADOS UNIDOS"), false)).cPaisRec);
        assertEquals(PaisType.BRA, config(cliente("A", "12345678", pais(" br ", "Brasil"), false)).cPaisRec);
    }

    @Test
    void codigoSinEquivalenteSifen_cortaConMensaje() {
        for (String codigo : new String[]{"PAR", "XX", null}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> config(cliente("A", "12345678", pais(codigo, "RARO"), false)));
            assertTrue(e.getMessage().contains("no tiene codigo SIFEN"), e.getMessage());
        }
    }

    @Test
    void extranjeroSinNombre_esInnominado() {
        ConfiguracionReceptor c = config(cliente("", "12345678901", pais("BR", "Brasil"), false));

        assertEquals(TiTipDocRec.INNOMINADO, c.iTipIDRec);
        assertEquals(PaisType.PRY, c.cPaisRec);
    }

    // ---- paraguayo: sin cambios ----

    @Test
    void paisParaguayExplicito_esNacional() {
        ConfiguracionReceptor c = config(cliente("JUAN", "4043581", pais("PY", "Paraguay"), false));

        assertEquals(TiTiOpe.B2C, c.iTiOpe);
        assertEquals(PaisType.PRY, c.cPaisRec);
        assertEquals(TiTipDocRec.CEDULA_PARAGUAYA, c.iTipIDRec);
        assertNull(c.dDirRec);
    }

    @Test
    void cedulaSinPais_sigueSiendoCedulaParaguaya() {
        assertEquals(TiTipDocRec.CEDULA_PARAGUAYA, config(cliente("JUAN", "4043581", null, false)).iTipIDRec);
        // las cedulas viejas tienen menos de 6 digitos: antes salian como "Otro" y rebotaban
        assertEquals(TiTipDocRec.CEDULA_PARAGUAYA, config(cliente("JUAN", "12345", null, false)).iTipIDRec);
    }

    @Test
    void contribuyenteSinPais_sigueSiendoB2b() {
        ConfiguracionReceptor c = config(cliente("EMPRESA X SA", "80099482", null, true));

        assertEquals(TiNatRec.CONTRIBUYENTE, c.iNatRec);
        assertEquals(TiTiOpe.B2B, c.iTiOpe);
        assertEquals(PaisType.PRY, c.cPaisRec);
    }

    @Test
    void sinCliente_esInnominado() {
        ConfiguracionReceptor c = SifenReceptorHelper.determinarConfiguracionReceptor(null, 100_000.0);

        assertEquals(TiTipDocRec.INNOMINADO, c.iTipIDRec);
    }

    // ---- sin pais y documento que no es cedula: se rechaza, nunca "Otro" ----

    @Test
    void sinPaisConDocumentoExtranjero_pideElPais() {
        for (String documento : new String[]{"12345678901", "FX123456"}) {
            ReceptorSinPaisException e = assertThrows(ReceptorSinPaisException.class,
                    () -> config(cliente("JOAO", documento, null, false)));
            assertTrue(e.getMessage().contains("elija su pais al facturar"), e.getMessage());
        }
    }

    // ---- documento guardado con DV: antes daba 9 digitos y caia en "Otro" ----

    @Test
    void rucConDvYTributa_esContribuyente() {
        ConfiguracionReceptor c = config(cliente("EMPRESA X SA", "80099482-5", null, true));

        assertEquals(TiNatRec.CONTRIBUYENTE, c.iNatRec);
        assertEquals("80099482", c.dRucRec);
    }

    @Test
    void cedulaConDvSinTributa_esCedulaParaguaya() {
        ConfiguracionReceptor c = config(cliente("JUAN", "4.043.581-2", null, false));

        assertEquals(TiTipDocRec.CEDULA_PARAGUAYA, c.iTipIDRec);
        assertEquals("4043581", c.dNumIDRec);
    }
}
