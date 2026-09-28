-- =====================================================================
-- precio_especial_sucursal: espejo del precio especial por sucursal
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- Las promos de una sola sucursal se hacian editando a mano precio_por_sucursal en la base de
-- la filial: el cambio no subia al central y un cambio posterior del central lo pisaba o lo
-- dejaba divergente. Esta tabla la escribe el central; cada filial toma las filas de su propia
-- sucursal y sustituye el precio al devolverlo (PrecioEspecialLector, que la lee por JDBC con
-- PrecioEspecialFuente). Ver central:docs/manuales-implementacion/productos/SPEC-PRECIO-ESPECIAL-SUCURSAL.md
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DE CENTRAL (V232.1)
--
-- Es MAIN_TO_ALL. Una vez que el central la agrega a central_pub, una filial SIN esta tabla corta
-- TODA su replicacion entrante (el apply worker cae en bucle con "logical replication target
-- relation does not exist" al primer especial), no solo el REFRESH. Por eso se verifica en CADA
-- filial antes de publicarla. Para una filial inalcanzable, este DDL es idempotente y se puede
-- correr a mano.
--
-- ESTE ES EL LADO SUBSCRIBER
--
-- Mismas columnas y tipos que el central, todas nullable. PK en id (la usa el apply para ubicar
-- la fila en UPDATE/DELETE). Sin FK, sin UNIQUE, sin CHECK, sin seed: las restricciones viven en
-- el central. El filial nunca escribe esta tabla.
-- =====================================================================
CREATE TABLE IF NOT EXISTS productos.precio_especial_sucursal (
    id           BIGSERIAL PRIMARY KEY,
    precio_id    BIGINT,
    sucursal_id  BIGINT,
    precio       NUMERIC,
    fecha_desde  DATE,
    fecha_hasta  DATE,
    activo       BOOLEAN,
    usuario_id   BIGINT,
    creado_en    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_precio_especial_sucursal_sucursal_precio
    ON productos.precio_especial_sucursal (sucursal_id, precio_id);

COMMENT ON TABLE productos.precio_especial_sucursal IS
    'Precio especial de un precio_por_sucursal en una sucursal, con vigencia. Llega por replicacion desde el central (MAIN_TO_ALL); el filial solo la lee.';
COMMENT ON COLUMN productos.precio_especial_sucursal.activo IS
    'Solo TRUE aplica. false o NULL = cortado.';
COMMENT ON COLUMN productos.precio_especial_sucursal.fecha_desde IS
    'NULL = desde siempre. Inclusivo, en hora de Paraguay (-03).';
COMMENT ON COLUMN productos.precio_especial_sucursal.fecha_hasta IS
    'NULL = sin fin. Inclusivo, en hora de Paraguay (-03).';
