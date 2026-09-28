package com.franco.dev.service.empresarial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fija el criterio de "de que sucursal es esta operacion".
 *
 * <p>El resolver de venta con tarjeta tomaba el {@code sucId} del cliente y lo usaba tal cual,
 * al reves de lo que hacen {@code VentaGraphQL} y {@code GastoGraphQL} en el mismo repo. Como
 * {@code venta_tarjeta} es BRANCH_TO_MAIN con PK compuesta {@code (id, sucursal_id)}, una fila
 * escrita con la sucursal de otro filial sube a central con atribucion falsa.
 */
public class SucursalServiceSucursalPropiaTest {

    private SucursalService service;
    private Environment env;

    @BeforeEach
    public void setUp() {
        env = mock(Environment.class);
        // @AllArgsConstructor: environment, repository, localService. Los dos ultimos no se
        // tocan en este camino.
        service = new SucursalService(env, null, null);
    }

    @Test
    public void sin_sucursal_del_cliente_se_usa_la_propia() {
        when(env.getProperty("sucursalId")).thenReturn("24");
        assertEquals(Long.valueOf(24), service.exigirSucursalPropia(null));
    }

    @Test
    public void la_propia_se_acepta() {
        when(env.getProperty("sucursalId")).thenReturn("24");
        assertEquals(Long.valueOf(24), service.exigirSucursalPropia(24L));
    }

    @Test
    public void una_sucursal_ajena_se_rechaza() {
        when(env.getProperty("sucursalId")).thenReturn("24");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.exigirSucursalPropia(7L));
        // El mensaje nombra las dos, porque si esto salta en produccion lo primero que se
        // pregunta es "cual creia cada uno".
        assertTrue(e.getMessage().contains("24"), e.getMessage());
        assertTrue(e.getMessage().contains("7"), e.getMessage());
    }

    @Test
    public void un_filial_sin_sucursalId_configurado_falla_claro() {
        when(env.getProperty("sucursalId")).thenReturn(null);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.exigirSucursalPropia(24L));
        assertTrue(e.getMessage().contains("sucursalId"), e.getMessage());
    }

    @Test
    public void un_sucursalId_en_blanco_se_trata_como_ausente() {
        // Un .properties con `sucursalId=` deja un string vacio, no un null. Sin esto,
        // Long.valueOf("") tiraria NumberFormatException y el mensaje no diria nada util.
        when(env.getProperty("sucursalId")).thenReturn("   ");
        assertThrows(IllegalStateException.class, () -> service.exigirSucursalPropia(null));
    }

    @Test
    public void se_tolera_espacio_alrededor_del_valor() {
        when(env.getProperty("sucursalId")).thenReturn(" 24 ");
        assertEquals(Long.valueOf(24), service.exigirSucursalPropia(24L));
    }
}
