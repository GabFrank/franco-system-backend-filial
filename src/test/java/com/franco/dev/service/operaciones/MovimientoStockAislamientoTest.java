package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Issue #166: save y saveAndSend pedian SERIALIZABLE sin que nadie reintentara. En ese nivel
 * Postgres cancela una de dos modificaciones simultaneas de movimientos vecinos (SQLState 40001)
 * y el movimiento no se guardaba. No protegia ningun calculo: el id sale de la secuencia.
 */
class MovimientoStockAislamientoTest {

    private static Transactional transaccionDe(String metodo, Class<?>... parametros) throws Exception {
        Method m = MovimientoStockService.class.getDeclaredMethod(metodo, parametros);
        Transactional t = m.getAnnotation(Transactional.class);
        assertNotNull(t, metodo + " tiene que seguir siendo transaccional");
        return t;
    }

    @Test
    @DisplayName("save no pide SERIALIZABLE")
    void saveNoPideSerializable() throws Exception {
        assertEquals(Isolation.DEFAULT, transaccionDe("save", MovimientoStock.class).isolation());
    }

    @Test
    @DisplayName("saveAndSend no pide SERIALIZABLE")
    void saveAndSendNoPideSerializable() throws Exception {
        assertEquals(Isolation.DEFAULT,
                transaccionDe("saveAndSend", MovimientoStock.class, Boolean.class).isolation());
    }
}
