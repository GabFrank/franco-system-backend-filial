package com.franco.dev.service.operaciones;

import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del guard de ventas duplicadas.
 *
 * <p>El que importa es {@link #dosHilosConLaMismaHuellaSoloUnoReserva()}: reproduce el mecanismo
 * de las ventas gemelas 25457/25458 de la caja 662, que la version anterior del servicio dejaba
 * pasar porque consultaba el cache y lo poblaba recien despues de persistir venta e items.
 */
class VentaDuplicadaCacheServiceTest {

    private static VentaItemInput item(Long productoId, Long presentacionId, Double cantidad) {
        VentaItemInput i = new VentaItemInput();
        i.setProductoId(productoId);
        i.setPresentacionId(presentacionId);
        i.setCantidad(cantidad);
        return i;
    }

    /** Los items de la venta 25457: 10 unidades del producto 11807. */
    private static List<VentaItemInput> itemsDeLaCaja662() {
        return Collections.singletonList(item(11807L, 11845L, 10.0));
    }

    @Test
    @DisplayName("dos hilos con la misma huella: solo uno reserva")
    void dosHilosConLaMismaHuellaSoloUnoReserva() throws Exception {
        VentaDuplicadaCacheService servicio = new VentaDuplicadaCacheService();
        int hilos = 16;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch llegada = new CountDownLatch(hilos);
        AtomicInteger reservaron = new AtomicInteger();
        AtomicInteger duplicados = new AtomicInteger();

        try {
            for (int i = 0; i < hilos; i++) {
                pool.execute(() -> {
                    try {
                        largada.await();
                        VentaDuplicadaCacheService.Reserva r =
                                servicio.reservar(48L, itemsDeLaCaja662());
                        if (r.esDuplicado()) {
                            duplicados.incrementAndGet();
                        } else {
                            reservaron.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        llegada.countDown();
                    }
                });
            }
            largada.countDown();
            assertTrue(llegada.await(10, TimeUnit.SECONDS), "los hilos no terminaron a tiempo");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, reservaron.get(), "exactamente un hilo tiene que quedarse con la huella");
        assertEquals(hilos - 1, duplicados.get(), "todos los demas tienen que salir como duplicado");
        assertEquals(1, servicio.getTamanioCache());
    }

    @Test
    @DisplayName("reservar dos veces seguidas: la segunda es duplicado")
    void reservarDosVecesSeguidas() {
        VentaDuplicadaCacheService servicio = new VentaDuplicadaCacheService();

        VentaDuplicadaCacheService.Reserva primera = servicio.reservar(48L, itemsDeLaCaja662());
        assertFalse(primera.esDuplicado());
        servicio.confirmar(primera, 25457L, LocalDateTime.now());

        VentaDuplicadaCacheService.Reserva segunda = servicio.reservar(48L, itemsDeLaCaja662());
        assertTrue(segunda.esDuplicado());
        assertEquals(Long.valueOf(25457L), segunda.getVentaIdPrevia(),
                "el mensaje tiene que poder nombrar la venta");
    }

    @Test
    @DisplayName("una excepcion intermedia libera la reserva en el acto, no a los 5 s")
    void unaExcepcionIntermediaLiberaLaReserva() {
        VentaDuplicadaCacheService servicio = new VentaDuplicadaCacheService();

        // Simula el camino de "Esta caja ya esta cerrada": se reserva y se rompe antes de vender.
        VentaDuplicadaCacheService.Reserva reserva = servicio.reservar(48L, itemsDeLaCaja662());
        boolean confirmada = false;
        try {
            throw new IllegalStateException("Esta caja ya esta cerrada");
        } catch (IllegalStateException esperada) {
            // el resolver deja que se propague; lo que importa es el finally
        } finally {
            if (!confirmada) {
                servicio.liberar(reserva);
            }
        }

        assertEquals(0, servicio.getTamanioCache(), "la huella tiene que quedar libre");
        VentaDuplicadaCacheService.Reserva reintento = servicio.reservar(48L, itemsDeLaCaja662());
        assertFalse(reintento.esDuplicado(), "el reintento legitimo no puede quedar bloqueado");
    }

    @Test
    @DisplayName("una reserva en vuelo no expira por tiempo")
    void unaReservaEnVueloNoExpiraPorTiempo() {
        AtomicReference<LocalDateTime> ahora = new AtomicReference<>(LocalDateTime.now());
        VentaDuplicadaCacheService servicio = new VentaDuplicadaCacheService(ahora::get);

        VentaDuplicadaCacheService.Reserva enVuelo = servicio.reservar(48L, itemsDeLaCaja662());
        assertFalse(enVuelo.esDuplicado());

        // La venta tarda mas que la ventana: contencion de la base, pool saturado.
        ahora.set(ahora.get().plusSeconds(60));

        VentaDuplicadaCacheService.Reserva gemela = servicio.reservar(48L, itemsDeLaCaja662());
        assertTrue(gemela.esDuplicado(), "una venta lenta no puede habilitar a la gemela");
        assertNull(gemela.getVentaIdPrevia(), "la anterior todavia no tiene id: el mensaje cambia");
    }

    @Test
    @DisplayName("pasada la ventana, una venta confirmada deja de bloquear")
    void pasadaLaVentanaLaVentaConfirmadaDejaDeBloquear() {
        AtomicReference<LocalDateTime> ahora = new AtomicReference<>(LocalDateTime.now());
        VentaDuplicadaCacheService servicio = new VentaDuplicadaCacheService(ahora::get);

        VentaDuplicadaCacheService.Reserva primera = servicio.reservar(48L, itemsDeLaCaja662());
        servicio.confirmar(primera, 25457L, ahora.get());

        ahora.set(ahora.get().plusSeconds(6));

        VentaDuplicadaCacheService.Reserva segunda = servicio.reservar(48L, itemsDeLaCaja662());
        assertFalse(segunda.esDuplicado(), "pasados los 5 s el cajero puede volver a vender lo mismo");
    }

    @Test
    @DisplayName("items con producto o presentacion en null no tiran NPE")
    void itemsConNullNoTiranNpe() {
        VentaDuplicadaCacheService servicio = new VentaDuplicadaCacheService();
        List<VentaItemInput> items = new ArrayList<>(Arrays.asList(
                item(null, null, 1.0),
                item(11807L, null, 2.0)));

        VentaDuplicadaCacheService.Reserva reserva = servicio.reservar(48L, items);
        assertNotNull(reserva);
        assertFalse(reserva.esDuplicado());

        assertTrue(servicio.reservar(48L, items).esDuplicado(), "la huella con nulls tiene que ser estable");
    }

    @Test
    @DisplayName("el orden de los items no cambia la huella")
    void elOrdenDeLosItemsNoCambiaLaHuella() {
        VentaDuplicadaCacheService servicio = new VentaDuplicadaCacheService();
        List<VentaItemInput> enUnOrden = Arrays.asList(item(1L, 10L, 1.0), item(2L, 20L, 3.0));
        List<VentaItemInput> alReves = Arrays.asList(item(2L, 20L, 3.0), item(1L, 10L, 1.0));

        assertFalse(servicio.reservar(48L, enUnOrden).esDuplicado());
        assertTrue(servicio.reservar(48L, alReves).esDuplicado());
    }

    @Test
    @DisplayName("otro usuario con los mismos items no queda bloqueado")
    void otroUsuarioNoQuedaBloqueado() {
        VentaDuplicadaCacheService servicio = new VentaDuplicadaCacheService();

        assertFalse(servicio.reservar(48L, itemsDeLaCaja662()).esDuplicado());
        assertFalse(servicio.reservar(52L, itemsDeLaCaja662()).esDuplicado(),
                "la huella incluye el usuario: otro cajero vende lo mismo sin trabarse");
    }

    @Test
    @DisplayName("sin usuario o sin items el guard no aplica")
    void sinUsuarioOSinItemsElGuardNoAplica() {
        VentaDuplicadaCacheService servicio = new VentaDuplicadaCacheService();

        assertFalse(servicio.reservar(null, itemsDeLaCaja662()).esDuplicado());
        assertFalse(servicio.reservar(48L, null).esDuplicado());
        assertFalse(servicio.reservar(48L, Collections.emptyList()).esDuplicado());
        assertEquals(0, servicio.getTamanioCache(), "nada de eso debe ocupar lugar en el cache");
    }

    @Test
    @DisplayName("la limpieza se lleva la reserva colgada")
    void laLimpiezaSeLlevaLaReservaColgada() {
        AtomicReference<LocalDateTime> ahora = new AtomicReference<>(LocalDateTime.now());
        VentaDuplicadaCacheService servicio = new VentaDuplicadaCacheService(ahora::get);

        servicio.reservar(48L, itemsDeLaCaja662());
        assertEquals(1, servicio.getTamanioCache());

        // El hilo murio entre reservar y confirmar/liberar: al minuto la red de seguridad la saca.
        ahora.set(ahora.get().plusMinutes(2));
        servicio.limpiarCache();

        assertEquals(0, servicio.getTamanioCache());
        assertFalse(servicio.reservar(48L, itemsDeLaCaja662()).esDuplicado());
    }
}
