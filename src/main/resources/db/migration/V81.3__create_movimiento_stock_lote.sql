-- Control de lotes - Fase 1 (Compras).
--
-- operaciones.movimiento_stock_lote es el LEDGER hijo de operaciones.movimiento_stock: desglosa
-- cada movimiento agregado en las cantidades que corresponden a cada numero de lote / vencimiento.
-- No es una tabla de saldos. El stock por lote se deriva sumando este ledger, exactamente igual
-- que el stock normal se deriva de SUM(movimiento_stock.cantidad).
--
-- Por que ledger y no una tabla con cantidad_disponible:
--   1) Un contador replicado bidireccionalmente sufre lost updates (el central suma por compra y
--      la filial resta por venta sobre la misma fila). Los conflictos de replicacion se saltean,
--      asi que el saldo quedaria desviado en silencio y de forma permanente. Cada fila de un
--      ledger es un INSERT con PK propia: nada se pisa.
--   2) Las rutas que deshacen una recepcion en el central BORRAN el movimiento_stock. Con
--      ON DELETE CASCADE la reversion por lote sale gratis, sin codigo que mantener en 3 lugares.
--
-- DDL espejo del central V153.3. Esta migracion va PRIMERO (antes que el central registre la
-- tabla en replication_table), sino el subscriber de esta filial rompe al refrescar la suscripcion.
--
-- En esta fase la filial solo RECIBE filas por replicacion (entradas por compra generadas en el
-- central). El descuento por venta es Fase 2.

CREATE TABLE IF NOT EXISTS operaciones.movimiento_stock_lote (
    id                  BIGSERIAL,
    sucursal_id         BIGINT NOT NULL,
    movimiento_stock_id BIGINT NOT NULL,
    producto_id         BIGINT NOT NULL,
    presentacion_id     BIGINT,
    numero_lote         VARCHAR(100) NOT NULL,
    fecha_vencimiento   DATE,
    cantidad            NUMERIC(15,4) NOT NULL,
    referencia          BIGINT,
    estado              BOOLEAN NOT NULL DEFAULT TRUE,
    usuario_id          BIGINT,
    creado_en           TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, sucursal_id),
    -- OJO: esta FK es de UNA sola columna, a diferencia del central (V153.3) que la tiene
    -- compuesta. No es un descuido: operaciones.movimiento_stock tiene claves distintas en cada
    -- base. En bodega3 la PK es (id, sucursal_id); en general6 es solo (id). Una FK compuesta
    -- falla aca con "no hay restriccion unique que coincida con las columnas dadas".
    -- El efecto practico es el mismo: ON DELETE CASCADE limpia el desglose por lote cuando se
    -- borra el movimiento agregado.
    CONSTRAINT fk_msl_movimiento
        FOREIGN KEY (movimiento_stock_id)
        REFERENCES operaciones.movimiento_stock (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_msl_producto     FOREIGN KEY (producto_id)     REFERENCES productos.producto(id),
    CONSTRAINT fk_msl_presentacion FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id),
    CONSTRAINT fk_msl_usuario      FOREIGN KEY (usuario_id)      REFERENCES personas.usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_msl_producto_sucursal
    ON operaciones.movimiento_stock_lote (producto_id, sucursal_id) WHERE estado = TRUE;
CREATE INDEX IF NOT EXISTS idx_msl_vencimiento
    ON operaciones.movimiento_stock_lote (fecha_vencimiento) WHERE estado = TRUE;
CREATE INDEX IF NOT EXISTS idx_msl_movimiento
    ON operaciones.movimiento_stock_lote (movimiento_stock_id, sucursal_id);

-- Necesario para que la replicacion logica pueda identificar filas en UPDATE/DELETE.
ALTER TABLE operaciones.movimiento_stock_lote REPLICA IDENTITY FULL;

-- ============================================================================
-- CRITICO: esquema de IDs par/impar para evitar colisiones de PK bajo replicacion.
--
-- Central y filial escriben en esta misma tabla con el mismo sucursal_id. Se replica el mismo
-- esquema que ya usa operaciones.movimiento_stock:
--   - Central  -> IDs IMPARES (generados en Java, ver MovimientoStockLoteService).
--   - Filial   -> IDs PARES   (esta secuencia, INCREMENT BY 2 arrancando en par).
--
-- Un BIGSERIAL plano en ambos lados colisiona apenas la filial empiece a descontar por venta.
-- Identico a V27__modif_mov_stock_seq.sql.
-- ============================================================================
DO $$
DECLARE
    last_id   BIGINT;
    new_start BIGINT;
BEGIN
    SELECT COALESCE(MAX(id), 0) INTO last_id FROM operaciones.movimiento_stock_lote;

    -- Dejar la secuencia posicionada de modo que el proximo nextval() caiga en PAR.
    IF mod(last_id, 2) = 0 THEN
        new_start := last_id;
    ELSE
        new_start := last_id + 1;
    END IF;

    RAISE NOTICE 'movimiento_stock_lote: last_id=%, new_start=%', last_id, new_start;

    PERFORM setval('operaciones.movimiento_stock_lote_id_seq', GREATEST(new_start, 2), true);
    EXECUTE 'ALTER SEQUENCE operaciones.movimiento_stock_lote_id_seq INCREMENT BY 2';
END $$;

-- Vista de stock por lote. No se replica: cada base la calcula sobre sus propias filas, lo que
-- permite a la filial consultar local sin latencia de red (requisito de performance del POS).
-- FEFO = ORDER BY fecha_vencimiento ASC NULLS LAST sobre esta vista.
CREATE OR REPLACE VIEW operaciones.v_stock_lote AS
SELECT producto_id,
       sucursal_id,
       numero_lote,
       fecha_vencimiento,
       SUM(cantidad) AS cantidad_disponible
FROM operaciones.movimiento_stock_lote
WHERE estado = TRUE
GROUP BY producto_id, sucursal_id, numero_lote, fecha_vencimiento
HAVING SUM(cantidad) <> 0;
