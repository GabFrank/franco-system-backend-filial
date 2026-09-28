package com.franco.dev.service.operaciones;

import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache en memoria que impide que la misma venta se registre dos veces.
 *
 * <p>La huella de una venta es su usuario mas la lista normalizada de items. Antes de tocar la
 * base, quien va a vender <b>reserva</b> esa huella; recien cuando la venta existe la
 * <b>confirma</b>, y si algo falla la <b>libera</b>. Reservar primero es lo que cierra la carrera:
 * la version anterior consultaba el cache y lo poblaba despues de persistir venta e items, asi que
 * dos requests concurrentes pasaban los dos (ventas gemelas 25457/25458 de la caja 662).
 *
 * <p>La atomicidad la dan las operaciones condicionales de {@link ConcurrentHashMap}
 * (putIfAbsent / replace / remove); no hace falta lock ni synchronized.
 */
@Service
public class VentaDuplicadaCacheService {

    private static final Logger log = LoggerFactory.getLogger(VentaDuplicadaCacheService.class);

    /** Ventana en la que una venta ya confirmada bloquea a otra identica. */
    private static final int TIEMPO_MINIMO_SEGUNDOS = 5;

    /** Red de seguridad para una reserva colgada: el hilo murio entre reservar y confirmar/liberar. */
    private static final int MINUTOS_RESERVA_COLGADA = 1;

    private final Map<String, Entrada> entradas = new ConcurrentHashMap<>();

    private final Supplier<LocalDateTime> reloj;

    public VentaDuplicadaCacheService() {
        this(LocalDateTime::now);
    }

    /** Constructor para los tests: permite adelantar el reloj sin dormir el hilo. */
    VentaDuplicadaCacheService(Supplier<LocalDateTime> reloj) {
        this.reloj = reloj;
    }

    /**
     * Toma la huella de la venta antes de que exista en la base.
     *
     * @return el token a pasarle despues a {@link #confirmar} o {@link #liberar}. Si el token dice
     *         {@link Reserva#esDuplicado()}, no hay que vender.
     */
    public Reserva reservar(Long usuarioId, List<VentaItemInput> ventaItemList) {
        if (usuarioId == null || ventaItemList == null || ventaItemList.isEmpty()) {
            return Reserva.NO_APLICA;
        }
        String huella = huella(usuarioId, ventaItemList);
        LocalDateTime ahora = reloj.get();

        while (true) {
            Entrada nuestra = new Entrada(ahora);
            Entrada previa = entradas.putIfAbsent(huella, nuestra);
            if (previa == null) {
                log.debug("Reserva tomada para usuario {} con {} items", usuarioId, ventaItemList.size());
                return new Reserva(huella, nuestra);
            }

            Long ventaIdPrevia = previa.ventaId;
            if (ventaIdPrevia == null) {
                // En vuelo: NO expira por tiempo. Si expirara, una venta lenta (contencion de la
                // base, pool saturado) dejaria pasar a la gemela y volveriamos al incidente
                // original, ahora disparado por lentitud en vez de por falta de lock.
                log.warn("VENTA DUPLICADA BLOQUEADA: el usuario {} ya tiene una venta identica en curso", usuarioId);
                return Reserva.duplicada(null);
            }
            if (previa.creadoEn.isAfter(ahora.minusSeconds(TIEMPO_MINIMO_SEGUNDOS))) {
                log.warn("VENTA DUPLICADA BLOQUEADA: el usuario {} ya registro la venta {} hace menos de {} s",
                        usuarioId, ventaIdPrevia, TIEMPO_MINIMO_SEGUNDOS);
                return Reserva.duplicada(ventaIdPrevia);
            }

            // Confirmada y vencida: la huella vuelve a estar libre. El replace condicional decide
            // quien se la queda si dos hilos llegan juntos; el que pierde reintenta y en la vuelta
            // siguiente ve la entrada nueva, en vuelo, y sale como duplicado.
            if (entradas.replace(huella, previa, nuestra)) {
                return new Reserva(huella, nuestra);
            }
        }
    }

    /** La venta existe: la reserva pasa a ser el antecedente que bloquea a la proxima gemela. */
    public void confirmar(Reserva reserva, Long ventaId, LocalDateTime creadoEn) {
        if (reserva == null || !reserva.tomada()) {
            return;
        }
        reserva.entrada.creadoEn = creadoEn != null ? creadoEn : reloj.get();
        reserva.entrada.ventaId = ventaId;
        log.debug("Reserva confirmada con la venta {}", ventaId);
    }

    /**
     * La venta no llego a existir: se suelta la huella en el acto.
     *
     * <p>Sin esto, un fallo ajeno al guard —una caja cerrada, un error de red— dejaria al cajero
     * bloqueado durante toda la ventana con un mensaje que no tiene nada que ver con la causa. El
     * rollback de la transaccion no alcanza a este mapa: la base deshace la venta, la memoria no.
     */
    public void liberar(Reserva reserva) {
        if (reserva == null || !reserva.tomada()) {
            return;
        }
        if (entradas.remove(reserva.huella, reserva.entrada)) {
            log.debug("Reserva liberada sin venta");
        }
    }

    /** Limpia lo vencido y, sobre todo, la reserva que quedo colgada sin confirmar ni liberar. */
    @Scheduled(fixedRate = 60000)
    public void limpiarCache() {
        LocalDateTime limite = reloj.get().minusMinutes(MINUTOS_RESERVA_COLGADA);
        int eliminadas = 0;
        for (Iterator<Map.Entry<String, Entrada>> it = entradas.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Entrada> e = it.next();
            if (e.getValue().creadoEn.isBefore(limite)) {
                it.remove();
                eliminadas++;
            }
        }
        if (eliminadas > 0) {
            log.debug("Cache de ventas duplicadas: {} entradas vencidas removidas", eliminadas);
        }
    }

    /** Tamanio actual del cache, para monitoreo. */
    public int getTamanioCache() {
        return entradas.size();
    }

    /**
     * Huella estable de la venta. El formateo es null-safe a proposito: un item sin producto o sin
     * presentacion tiene que dar una huella valida, no tumbar la venta entera con un NPE.
     */
    private String huella(Long usuarioId, List<VentaItemInput> ventaItemList) {
        List<String> partes = new ArrayList<>(ventaItemList.size());
        for (VentaItemInput item : ventaItemList) {
            partes.add(item.getProductoId() + "|" + item.getPresentacionId() + "|" + item.getCantidad());
        }
        Collections.sort(partes);
        return usuarioId + "#" + String.join(",", partes);
    }

    private static class Entrada {
        private volatile Long ventaId;
        private volatile LocalDateTime creadoEn;

        private Entrada(LocalDateTime creadoEn) {
            this.creadoEn = creadoEn;
        }
    }

    /** Token de una reserva. Lo devuelve {@link #reservar} y lo consumen confirmar/liberar. */
    public static class Reserva {

        private static final Reserva NO_APLICA = new Reserva(null, null);

        private final String huella;
        private final Entrada entrada;
        private final boolean duplicado;
        private final Long ventaIdPrevia;

        private Reserva(String huella, Entrada entrada) {
            this.huella = huella;
            this.entrada = entrada;
            this.duplicado = false;
            this.ventaIdPrevia = null;
        }

        private Reserva(Long ventaIdPrevia) {
            this.huella = null;
            this.entrada = null;
            this.duplicado = true;
            this.ventaIdPrevia = ventaIdPrevia;
        }

        private static Reserva duplicada(Long ventaIdPrevia) {
            return new Reserva(ventaIdPrevia);
        }

        /** True si hay que abortar: ya existe una venta identica, terminada o en curso. */
        public boolean esDuplicado() {
            return duplicado;
        }

        /** Id de la venta que bloquea, o null si la anterior todavia esta en vuelo. */
        public Long getVentaIdPrevia() {
            return ventaIdPrevia;
        }

        private boolean tomada() {
            return !duplicado && entrada != null;
        }
    }
}
