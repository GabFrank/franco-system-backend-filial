package com.franco.dev.service.financiero;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El lock con espera acotada se pide una sola vez por transacción y por clave (#185): cada pedido son
 * cuatro consultas nativas que vuelcan la sesión, y una venta lo pide por cada ítem con lote. Una
 * transacción nueva (o un REQUIRES_NEW, otra conexión) lo vuelve a pedir.
 */
class BloqueoTransaccionalServiceTest {

    /** current_setting, set_config, pg_advisory_xact_lock y set_config. */
    private static final int CONSULTAS_POR_PEDIDO = 4;

    private EntityManager entityManager;
    private BloqueoTransaccionalService service;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn("0");
        service = new BloqueoTransaccionalService();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void laMismaClaveEnLaMismaTransaccionSePideUnaSolaVez() {
        TransactionSynchronizationManager.initSynchronization();

        service.tomar("lote-saldo:24", "15s");
        service.tomar("lote-saldo:24", "15s");
        service.tomar("lote-saldo:24", "15s");

        verify(entityManager, times(CONSULTAS_POR_PEDIDO)).createNativeQuery(anyString());
    }

    @Test
    void otraClaveSePide() {
        TransactionSynchronizationManager.initSynchronization();

        service.tomar("lote-saldo:24", "15s");
        service.tomar("lote-saldo:20", "15s");

        verify(entityManager, times(2 * CONSULTAS_POR_PEDIDO)).createNativeQuery(anyString());
    }

    @Test
    void unaTransaccionNuevaLaVuelveAPedir() {
        TransactionSynchronizationManager.initSynchronization();
        service.tomar("lote-saldo:24", "15s");
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        service.tomar("lote-saldo:24", "15s");

        verify(entityManager, times(2 * CONSULTAS_POR_PEDIDO)).createNativeQuery(anyString());
    }

    @Test
    void sinSincronizacionActivaSePideSiempre() {
        service.tomar("lote-saldo:24", "15s");
        service.tomar("lote-saldo:24", "15s");

        verify(entityManager, times(2 * CONSULTAS_POR_PEDIDO)).createNativeQuery(anyString());
    }

    @Test
    void usaHashtextQueExisteEnCualquierVersionDePostgres() {
        service.tomar("lote-saldo:24", "15s");

        verify(entityManager).createNativeQuery(contains("pg_advisory_xact_lock(hashtext(:clave))"));
    }
}
