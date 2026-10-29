-- =====================================================================
-- costo_por_producto: columnas del costo en la moneda de costeo, espejo del central (V259.5)
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- El central pasa a guardar el costo de cada compra dos veces: en las columnas de hoy, que siguen
-- en la moneda de la empresa (de ahi salen venta_item.costo_unitario, el lucro y los reportes), y
-- en cuatro columnas nuevas en la moneda de costeo (modulo COSTOS de la moneda por modulo). Con
-- COSTOS = moneda de la empresa, las nuevas son copia de las de hoy. El filial no las lee.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DEL CENTRAL
--
-- costo_por_producto es MAIN_TO_ALL (central_pub). Cuando el central escriba una fila con las
-- columnas nuevas, una filial sin ellas deja al apply worker en crash-loop ("missing replicated
-- column"). Por eso se despliega, y se verifica en flyway_schema_history de cada filial, antes
-- que el central.
--
-- ESTE ES EL LADO SUBSCRIBER
--
-- Mismos tipos que el central, nullable, sin default ni FK: las restricciones viven en el
-- publisher. La entidad del filial no las mapea (ddl-auto=none), asi que no cambia nada mas.
-- =====================================================================
ALTER TABLE productos.costo_por_producto ADD COLUMN IF NOT EXISTS moneda_costeo_id BIGINT;
ALTER TABLE productos.costo_por_producto ADD COLUMN IF NOT EXISTS costo_medio_costeo NUMERIC;
ALTER TABLE productos.costo_por_producto ADD COLUMN IF NOT EXISTS ultimo_precio_compra_costeo NUMERIC;
ALTER TABLE productos.costo_por_producto ADD COLUMN IF NOT EXISTS cotizacion_costeo NUMERIC;

COMMENT ON COLUMN productos.costo_por_producto.moneda_costeo_id IS
    'Moneda de costeo (modulo COSTOS) con que se calcularon las columnas *_costeo. NULL = fila anterior a la moneda por modulo.';
COMMENT ON COLUMN productos.costo_por_producto.costo_medio_costeo IS
    'Costo medio en la moneda de costeo. costo_medio sigue en la moneda de la empresa.';
COMMENT ON COLUMN productos.costo_por_producto.ultimo_precio_compra_costeo IS
    'Ultimo precio de compra en la moneda de costeo. ultimo_precio_compra sigue en la moneda de la empresa.';
COMMENT ON COLUMN productos.costo_por_producto.cotizacion_costeo IS
    'Guaranies por unidad de la moneda de costeo con que se calculo la fila (1 si es el guarani).';
