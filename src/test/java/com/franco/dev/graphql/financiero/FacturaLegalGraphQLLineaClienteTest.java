package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** La linea del cliente en la factura autoimpresa no rompe la impresion por el largo del nombre (#213). */
class FacturaLegalGraphQLLineaClienteTest {

    private static Cliente clienteConNombre(String nombre) {
        Persona persona = new Persona();
        persona.setNombre(nombre);
        Cliente cliente = new Cliente();
        cliente.setPersona(persona);
        return cliente;
    }

    @Test
    void unNombreCortoSaleEntero() {
        assertEquals("Cliente: JUAN PEREZ GOMEZ", FacturaLegalGraphQL.lineaCliente(clienteConNombre("JUAN PEREZ GOMEZ")));
    }

    @Test
    void unNombreDe22SaleEntero() {
        String nombre = "ABCDEFGHIJKLMNOPQRSTUV";
        assertEquals("Cliente: " + nombre, FacturaLegalGraphQL.lineaCliente(clienteConNombre(nombre)));
    }

    @Test
    void unNombreLargoSeRecortaA22() {
        assertEquals("Cliente: ABCDEFGHIJKLMNOPQRSTUV",
                FacturaLegalGraphQL.lineaCliente(clienteConNombre("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456")));
    }

    @Test
    void sinNombreNoHayLinea() {
        assertNull(FacturaLegalGraphQL.lineaCliente(clienteConNombre(null)));
    }

    @Test
    void sinPersonaNoHayLinea() {
        assertNull(FacturaLegalGraphQL.lineaCliente(new Cliente()));
    }
}
