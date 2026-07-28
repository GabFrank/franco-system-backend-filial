-- Control de lotes - Maestro de lotes.
--
-- operaciones.lote convierte al lote en una ENTIDAD en vez de texto repetido en cada fila del
-- ledger operaciones.movimiento_stock_lote. Es el patron de stock.lot en Odoo y de batch a nivel
-- material en SAP: la unicidad es (producto, numero_lote), porque el numero de lote lo asigna el
-- fabricante por producto (GS1 identifica el lote siempre junto al GTIN).
--
-- Que resuelve:
--   1) La fecha de vencimiento deja de estar desnormalizada en cada movimiento. Dos recepciones
--      del mismo lote con fechas tipeadas distinto ya no producen dos lotes distintos.
--   2) Da lugar a los datos del lote: proveedor, fabricacion, fecha de retiro.
--   3) Habilita el bloqueo por recall via la columna estado, sin tocar el stock fisico.
--
-- DDL espejo del central V155.3. Esta migracion va PRIMERO (antes de que el central registre
-- operaciones.lote en replication_table), sino el subscriber de esta filial rompe.
--
-- En esta fase la filial solo RECIBE lotes por replicacion (MAIN_TO_ALL): el maestro se administra
-- exclusivamente en el central, que es donde ocurren las recepciones. El consumo por venta con
-- FEFO es Etapa 2.

CREATE TABLE IF NOT EXISTS operaciones.lote (
    -- BIGSERIAL simple: a diferencia de movimiento_stock_lote, aca NO hace falta el esquema
    -- par/impar porque la filial nunca escribe lotes. Solo el central los crea.
    id                BIGSERIAL PRIMARY KEY,
    producto_id       BIGINT NOT NULL,
    numero_lote       VARCHAR(100) NOT NULL,
    fecha_vencimiento DATE,
    -- FEFO ordena por esta fecha, no por el vencimiento: se calcula restando
    -- producto.dias_vencimiento al vencimiento, para sacar la mercaderia antes de que venza.
    fecha_retiro      DATE,
    fecha_fabricacion DATE,
    proveedor_id      BIGINT,
    -- LIBERADO | CUARENTENA | BLOQUEADO. Solo los LIBERADO entran en FEFO y se pueden vender.
    estado            VARCHAR(20) NOT NULL DEFAULT 'LIBERADO',
    observacion       VARCHAR(500),
    usuario_id        BIGINT,
    creado_en         TIMESTAMP NOT NULL DEFAULT NOW(),
    actualizado_en    TIMESTAMP,
    CONSTRAINT uq_lote_producto_numero UNIQUE (producto_id, numero_lote),
    CONSTRAINT ck_lote_estado CHECK (estado IN ('LIBERADO', 'CUARENTENA', 'BLOQUEADO')),
    CONSTRAINT fk_lote_producto  FOREIGN KEY (producto_id) REFERENCES productos.producto(id),
    CONSTRAINT fk_lote_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id),
    CONSTRAINT fk_lote_usuario   FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_lote_producto   ON operaciones.lote (producto_id);
CREATE INDEX IF NOT EXISTS idx_lote_retiro     ON operaciones.lote (fecha_retiro) WHERE estado = 'LIBERADO';

ALTER TABLE operaciones.lote REPLICA IDENTITY FULL;

-- ============================================================================
-- Vinculo del ledger con el maestro.
--
-- CRITICO: en la FILIAL este vinculo va SIN foreign key, a proposito.
--
-- operaciones.lote viaja por central_pub (MAIN_TO_ALL) y operaciones.movimiento_stock_lote por
-- central_filialX_pub (filtrada por sucursal_id). Son publicaciones distintas, asi que NO hay
-- garantia de orden entre ellas: la fila del ledger puede llegar antes que la del lote. Con una FK
-- eso seria una violacion de integridad y la replicacion de esta filial se detiene.
--
-- En el central (V155.3) la FK SI existe, porque alli lote y movimiento se crean en la misma
-- transaccion. Aca alcanza con el indice para las consultas.
-- ============================================================================
ALTER TABLE operaciones.movimiento_stock_lote ADD COLUMN IF NOT EXISTS lote_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_msl_lote ON operaciones.movimiento_stock_lote (lote_id);

-- numero_lote se mantiene desnormalizado en el ledger: es inmutable (parte de la identidad del
-- lote), nunca diverge, y deja las filas legibles aunque el maestro llegue con retraso.
--
-- fecha_vencimiento queda DEPRECADA en el ledger: se deja de escribir y de leer, la fuente de
-- verdad pasa a ser operaciones.lote. No se elimina aca por la regla de 2 versiones de Flyway.
COMMENT ON COLUMN operaciones.movimiento_stock_lote.fecha_vencimiento IS
    'DEPRECADO desde V82.3: la fecha de vencimiento vive en operaciones.lote. No escribir ni leer.';

-- ============================================================================
-- SIN BACKFILL EN LA FILIAL - a proposito.
--
-- Si esta migracion creara filas de lote a partir del ledger local, usaria su propia secuencia
-- BIGSERIAL. Cuando despues bajara la fila equivalente del central (mismo producto_id y
-- numero_lote pero OTRO id), chocaria contra uq_lote_producto_numero y la replicacion de esta
-- filial se detendria.
--
-- El central hace el backfill (V155.3) y de ahi bajan dos cosas por replicacion:
--   - las filas de operaciones.lote, por central_pub (MAIN_TO_ALL);
--   - el UPDATE que setea lote_id en movimiento_stock_lote, por central_filialX_pub.
--
-- Nota ops: el REFRESH PUBLICATION usa copy_data = false, asi que los lotes YA existentes en el
-- central no bajan retroactivamente. Mientras falten, el LEFT JOIN de la vista degrada sin romper:
-- numero_lote se sigue viendo y el saldo se sigue calculando, solo quedan nulos vencimiento,
-- retiro y estado. Para bajar lotes preexistentes, re-guardarlos en el central (dispara un UPDATE
-- que si se replica).
-- ============================================================================

-- Vista de stock por lote, ahora enriquecida con el maestro.
-- El saldo se sigue derivando del ledger: el modelo no cambia, solo se resuelven los datos del lote.
-- FEFO = ORDER BY COALESCE(fecha_retiro, fecha_vencimiento) ASC NULLS LAST sobre esta vista,
-- filtrando estado = 'LIBERADO'.
--
-- Se hace DROP + CREATE y no CREATE OR REPLACE porque cambia la lista de columnas de la vista
-- (se agregan lote_id, fecha_retiro y estado), y CREATE OR REPLACE VIEW no lo permite.
-- Es seguro: una vista es derivada, no contiene datos.
DROP VIEW IF EXISTS operaciones.v_stock_lote;

CREATE VIEW operaciones.v_stock_lote AS
SELECT msl.producto_id,
       msl.sucursal_id,
       msl.lote_id,
       msl.numero_lote,
       l.fecha_vencimiento,
       l.fecha_retiro,
       l.estado,
       SUM(msl.cantidad) AS cantidad_disponible
FROM operaciones.movimiento_stock_lote msl
LEFT JOIN operaciones.lote l ON l.id = msl.lote_id
WHERE msl.estado = TRUE
GROUP BY msl.producto_id, msl.sucursal_id, msl.lote_id, msl.numero_lote,
         l.fecha_vencimiento, l.fecha_retiro, l.estado
HAVING SUM(msl.cantidad) <> 0;
