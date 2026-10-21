package com.franco.dev.service.financiero;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.Set;

/**
 * Lock por nombre, hasta el fin de la transacción ({@code pg_advisory_xact_lock}). Espejo del servicio
 * del central (#463), reducido a lo que usa el filial.
 *
 * <p>La clave se reduce a un entero con {@code hashtext} y no con {@code hashtextextended} como en el
 * central: esa función es de PostgreSQL 11+ y no todas las bases filiales están verificadas. Dos claves
 * distintas que coincidan solo se esperan una a la otra, no fallan.</p>
 */
@Service
public class BloqueoTransaccionalService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Toma el lock de {@code clave}; la espera se corta a los {@code esperaMaxima} (formato de
     * {@code lock_timeout}, por ejemplo {@code "15s"}) con el error {@code 55P03} de PostgreSQL, que
     * aborta la transacción. El {@code lock_timeout} vale solo para esta espera: después se restaura.
     *
     * <p>Una clave ya tomada en esta transacción no se vuelve a pedir: el lock dura hasta el commit, y
     * cada consulta nativa vuelca la sesión entera.</p>
     *
     * <p>⚠️ No usarlo dentro de un savepoint ({@code PROPAGATION_NESTED}): un
     * {@code ROLLBACK TO SAVEPOINT} suelta el lock pero el registro de claves sigue diciendo que está.
     * Hoy ningún camino usa savepoints.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void tomar(String clave, String esperaMaxima) {
        Set<String> tomadas = tomadasEnEstaTransaccion();
        if (tomadas != null && tomadas.contains(clave)) {
            return;
        }
        String previo = (String) entityManager.createNativeQuery("select current_setting('lock_timeout')")
                .getSingleResult();
        fijarLockTimeout(esperaMaxima);
        // El cast: la función devuelve void y Hibernate no sabe mapear ese tipo (JDBC 1111).
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtext(:clave)) as varchar)")
                .setParameter("clave", clave)
                .getSingleResult();
        fijarLockTimeout(previo);
        if (tomadas != null) {
            tomadas.add(clave);
        }
    }

    /**
     * Las claves que esta transacción ya tiene, colgadas de una sincronización de la propia transacción:
     * Spring la descarta al terminar, y un {@code REQUIRES_NEW} (otra conexión, que no tiene el lock)
     * arranca con la suya vacía. Null si no hay sincronización activa: entonces se pide siempre.
     */
    private static Set<String> tomadasEnEstaTransaccion() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return null;
        }
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            if (s instanceof ClavesTomadas) {
                return ((ClavesTomadas) s).claves;
            }
        }
        ClavesTomadas nuevas = new ClavesTomadas();
        TransactionSynchronizationManager.registerSynchronization(nuevas);
        return nuevas.claves;
    }

    /** Spring 5.1: la interfaz todavía no tiene métodos default, hay que extender el adapter. */
    private static final class ClavesTomadas extends TransactionSynchronizationAdapter {
        private final Set<String> claves = new HashSet<>();
    }

    private void fijarLockTimeout(String valor) {
        // set_config(..., true) es el SET LOCAL de una función: dura hasta el fin de la transacción.
        entityManager.createNativeQuery("select set_config('lock_timeout', :valor, true)")
                .setParameter("valor", valor)
                .getSingleResult();
    }
}
