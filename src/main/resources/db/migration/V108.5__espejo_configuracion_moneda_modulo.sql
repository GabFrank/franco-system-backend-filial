-- =====================================================================
-- configuracion_moneda_modulo + moneda.codigo_iso: espejos del central (V258.5)
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- La moneda con la que nacen los documentos de cada modulo (COMPRAS, COSTOS, PRECIOS, PDV) pasa
-- a ser configurable desde el central. Cada fila dice "desde vigente_desde, el modulo X usa la
-- moneda Y"; solo el PDV admite una fila por sucursal. Una fila nueva reemplaza a la anterior y
-- ninguna se edita: el historial es la tabla misma.
--
-- Tabla VACIA = comportamiento de hoy: todo resuelve a la moneda de la empresa (la marcada como
-- principal, y si no hay ninguna, PYG). El filial todavia no la lee: la va a leer el PDV en su
-- propia entrega. Llega ahora para que la publicacion del central no se trabe.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DEL CENTRAL
--
-- Las dos tablas son MAIN_TO_ALL. Cuando el central agregue configuracion_moneda_modulo a
-- central_pub y refresque las suscripciones, el REFRESH de una filial sin la tabla falla entero.
-- Y cuando el central escriba moneda.codigo_iso, una filial sin la columna deja al apply worker
-- en crash-loop ("missing replicated column"). Por eso esta migracion se despliega, y se verifica
-- en flyway_schema_history de cada filial, antes que la del central.
--
-- ESTE ES EL LADO SUBSCRIBER
--
-- Mismas columnas y tipos que el central, todas nullable. PK en id (la usa el apply para ubicar
-- la fila en UPDATE/DELETE). Sin FK, sin UNIQUE, sin CHECK, sin seed: las restricciones viven en
-- el central, que es el publisher. El filial nunca escribe esta tabla.
-- =====================================================================
CREATE TABLE IF NOT EXISTS financiero.configuracion_moneda_modulo (
    id            BIGSERIAL PRIMARY KEY,
    modulo        VARCHAR(20),
    sucursal_id   BIGINT,
    moneda_id     BIGINT,
    vigente_desde TIMESTAMP,
    activo        BOOLEAN,
    usuario_id    BIGINT,
    creado_en     TIMESTAMP
);

COMMENT ON TABLE financiero.configuracion_moneda_modulo IS
    'Moneda de cada modulo. Llega por replicacion desde el central (MAIN_TO_ALL); el filial solo la lee. Sin filas = la moneda de la empresa.';

COMMENT ON COLUMN financiero.configuracion_moneda_modulo.modulo IS
    'COMPRAS, COSTOS, PRECIOS o PDV. Un valor desconocido hace que la fila se ignore.';

COMMENT ON COLUMN financiero.configuracion_moneda_modulo.sucursal_id IS
    'NULL = todas las sucursales. Solo el modulo PDV admite una fila por sucursal.';

COMMENT ON COLUMN financiero.configuracion_moneda_modulo.vigente_desde IS
    'Desde cuando rige. Afecta solo a los documentos que nacen despues; los abiertos terminan en su moneda.';

COMMENT ON COLUMN financiero.configuracion_moneda_modulo.activo IS
    'false = la fila se ignora y rige la anterior de la misma clave. NULL = activa.';

-- moneda es MAIN_TO_ALL: la columna tiene que existir aca antes de que el central la escriba.
-- Mismo tipo que el central, sin el indice unico (vive en el publisher).
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS codigo_iso VARCHAR(3);

COMMENT ON COLUMN financiero.moneda.codigo_iso IS
    'Codigo ISO 4217 (PYG, USD, BRL). Lo escribe el central; identifica la moneda sin mirar su nombre.';
