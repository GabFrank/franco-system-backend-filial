-- Unificación de Tipos de Gasto: "Módulo Padre" pasa a ser la única dimensión.
-- La clasificación/agrupación libre queda deprecada. Las columnas físicas
-- (is_clasificacion, clasificacion_gasto_id) se conservan por compatibilidad de
-- replicación lógica; aquí solo se normalizan los datos.

-- Los tipos que estaban marcados como clasificación (grupo) pasan a ser tipos normales.
UPDATE financiero.tipo_gasto
SET is_clasificacion = false
WHERE is_clasificacion IS DISTINCT FROM false;

-- La agrupación jerárquica ya no se usa: se limpia cualquier referencia de padre.
UPDATE financiero.tipo_gasto
SET clasificacion_gasto_id = NULL
WHERE clasificacion_gasto_id IS NOT NULL;

-- Todo tipo de gasto debe tener un módulo padre; los que no lo tengan pasan a OTRO.
UPDATE financiero.tipo_gasto
SET modulo_padre = 'OTRO'
WHERE modulo_padre IS NULL;
